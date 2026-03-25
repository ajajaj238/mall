package com.hmall.trade.listener;

import com.hmall.api.client.PayClient;
import com.hmall.api.dto.PayOrderDTO;
import com.hmall.trade.constants.MQconstants;
import com.hmall.trade.domin.po.Order;
import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderDelayMessageListener {
    private final IOrderService orderService;
    private final PayClient payClient;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MQconstants.DELAY_ORDER_QUEUE_NAME),
            exchange = @Exchange(name = MQconstants.DELAY_EXCHANGE_NAME, delayed = "true"),
            key = MQconstants.DELAY_ORDER_KEY
    ))
    public void listenerOrderDelayMessage(Long orderId) {
        //查询订单状态
        Order order = orderService.getById(orderId);
        //检测订单状态
        if (order == null || order.getStatus() != 1) {
            //如果订单不存在或已经支付，则返回
            return;
        }

        //未支付，需要查询支付流水状态
        PayOrderDTO payOrderDTO = payClient.queryPayOrderByBizOrderNo(orderId);

        //判断是否支付
        if (payOrderDTO != null && payOrderDTO.getStatus() == 3) {
            //已支付，则修改订单状态为已支付
            orderService.markOrderPaySuccess(orderId);
        }else {
            //未支付，则取消订单，恢复库存
            orderService.cancelOrder(orderId);
        }


    }
}
