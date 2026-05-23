package com.hmall.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.client.CartClient;
import com.hmall.api.client.ItemClient;
import com.hmall.api.client.PayClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.api.dto.PayOrderDTO;
import com.hmall.common.enums.OrderStatus;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.UserContext;
import com.hmall.trade.constants.MQconstants;
import com.hmall.trade.domin.dto.OrderFormDTO;
import com.hmall.trade.domin.po.Order;
import com.hmall.trade.domin.po.OrderDetail;
import com.hmall.trade.mapper.OrderMapper;
import com.hmall.trade.service.IOrderDetailService;
import com.hmall.trade.service.IOrderService;
import com.hmall.trade.service.IdempotentService;
import com.hmall.trade.statemachine.OrderStateMachine;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author zhj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {
    private final ItemClient itemClient;
    private final IOrderDetailService detailService;
    private final CartClient cartClient;
    private final RabbitTemplate rabbitTemplate;
    private final IdempotentService idempotentService;
    private final PayClient payClient;
    private final OrderStateMachine orderStateMachine;

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // 1. 幂等性检测(如果前端传了Token才验证)
        Long userId = UserContext.getUser();
        String token = orderFormDTO.getIdempotentToken();
        if (token != null && !token.isEmpty()) {
            idempotentService.validateAndConsumeTokenOrThrow(token, userId);
        }
        
        // 2. 查询商品信息
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        
        // 3. 计算总价
        int total = calculateTotalFee(items, itemNumMap);
        
        // 4. 创建订单
        Order order = buildOrder(orderFormDTO, userId, total);
        save(order);
        log.info("订单已创建: orderId={}, status={}", order.getId(), order.getStatus());
        
        // 5. 保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);
        
        // 6. 清理购物车
        cartClient.deleteCartItemByIds(itemIds);
        
        // 7. 扣减库存（Seata分布式事务）
        try {
            itemClient.deductStock(detailDTOS);
        } catch (Exception e) {
            log.error("库存扣减失败: orderId={}", order.getId(), e);
            throw new RuntimeException("库存不足！");
        }
        
        // 8. 发送延迟消息（30分钟后检查订单支付状态）
        try {
            rabbitTemplate.convertAndSend(
                    MQconstants.DELAY_EXCHANGE_NAME,
                    MQconstants.DELAY_ORDER_KEY,
                    order.getId(),
                    new MessagePostProcessor() {
                        @Override
                        public Message postProcessMessage(Message message) throws AmqpException {
                            message.getMessageProperties().setDelay(1800000);
                            return message;
                        }
                    });
            log.info("延迟消息已发送: orderId={}, delay=30min", order.getId());
        } catch (Exception e) {
            log.error("延迟消息发送失败，将由定时任务兜底: orderId={}", order.getId(), e);
        }
        
        return order.getId();
    }
    
    /**
     * 计算订单总价
     */
    private int calculateTotalFee(List<ItemDTO> items, Map<Long, Integer> itemNumMap) {
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        return total;
    }
    
    /**
     * 构建订单对象
     */
    private Order buildOrder(OrderFormDTO orderFormDTO, Long userId, int totalFee) {
        Order order = new Order();
        order.setTotalFee(totalFee);
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getValue());
        return order;
    }
    
    /**
     * 定时扫描超时未支付订单（每15分钟执行一次，兜底RabbitMQ延迟消息）
     */
    @Scheduled(fixedDelay = 900000)
    public void scanExpiredPendingOrders() {
        // 查询创建超过30分钟且状态为待支付的订单
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(30);
        List<Order> expiredOrders = lambdaQuery()
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.getValue())
                .le(Order::getCreateTime, deadline)
                .list();
        
        if (expiredOrders.isEmpty()) {
            return;
        }
        
        log.info("扫描到超时未支付订单: count={}", expiredOrders.size());
        for (Order expiredOrder : expiredOrders) {
            try {
                Long orderId = expiredOrder.getId();
                // 二次确认：检查支付流水是否已支付
                PayOrderDTO payOrderDTO = payClient.queryPayOrderByBizOrderNo(orderId);
                if (payOrderDTO != null && payOrderDTO.getStatus() == 3) {
                    // 已支付，标记订单支付成功
                    markOrderPaySuccess(orderId);
                    log.info("定时任务兜底-订单已支付: orderId={}", orderId);
                } else {
                    // 未支付，取消订单
                    cancelOrder(orderId);
                    payClient.closePayOrderByBizOrderNo(orderId);
                    log.info("定时任务兜底-订单已取消: orderId={}", orderId);
                }
            } catch (Exception e) {
                log.error("定时任务处理超时订单失败: orderId={}", expiredOrder.getId(), e);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markOrderPaySuccess(Long orderId) {
        Order currentOrder = getById(orderId);
        if (currentOrder == null) {
            throw new BadRequestException("订单不存在");
        }
        
        // 使用状态机验证状态流转
        orderStateMachine.validateTransition(currentOrder.getStatus(), OrderStatus.PAID.getValue());
        
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.PAID.getValue());
        order.setPayTime(LocalDateTime.now());
        updateById(order);
        log.info("订单支付成功: orderId={}", orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        Order currentOrder = getById(orderId);
        if (currentOrder == null) {
            throw new BadRequestException("订单不存在");
        }
        
        // 使用状态机验证状态流转
        orderStateMachine.validateTransition(currentOrder.getStatus(), OrderStatus.CANCELLED.getValue());
        
        // 取消订单
        Order order = new Order();
        order.setId(orderId);
        order.setCloseTime(LocalDateTime.now());
        order.setStatus(OrderStatus.CANCELLED.getValue());
        updateById(order);
        
        // 恢复库存
        restoreStock(orderId);
        log.info("订单已取消: orderId={}", orderId);
    }
    
    /**
     * 恢复库存
     */
    private void restoreStock(Long orderId) {
        List<OrderDetailDTO> detailDTOs = new ArrayList<>();
        List<OrderDetail> orderDetailList = detailService.lambdaQuery()
                .eq(OrderDetail::getOrderId, orderId).list();

        for (OrderDetail orderDetail : orderDetailList) {
            OrderDetailDTO orderDetailDTO = new OrderDetailDTO();
            orderDetailDTO.setNum(-orderDetail.getNum());
            orderDetailDTO.setItemId(orderDetail.getItemId());
            detailDTOs.add(orderDetailDTO);
        }
        itemClient.deductStock(detailDTOs);
        log.info("库存恢复成功: orderId={}", orderId);
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }
}
