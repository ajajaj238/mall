package com.hmall.trade.statemachine;

import com.hmall.common.enums.OrderStatus;
import com.hmall.common.exception.BizIllegalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 订单状态机
 * 规范订单状态流转，防止非法状态变更
 */
@Slf4j
@Component
public class OrderStateMachine {
    
    /**
     * 状态流转规则
     * key: 当前状态
     * value: 允许流转到的状态列表
     */
    private static final Map<OrderStatus, List<OrderStatus>> STATE_TRANSITIONS = new HashMap<>();
    
    static {
        // 待支付 -> 已支付、已取消
        STATE_TRANSITIONS.put(OrderStatus.PENDING_PAYMENT, 
            Arrays.asList(OrderStatus.PAID, OrderStatus.CANCELLED));
        
        // 已支付 -> 已发货、已取消(退款)
        STATE_TRANSITIONS.put(OrderStatus.PAID, 
            Arrays.asList(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        
        // 已发货 -> 已完成、已取消(退货)
        STATE_TRANSITIONS.put(OrderStatus.SHIPPED, 
            Arrays.asList(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        
        // 已完成 -> 已评价
        STATE_TRANSITIONS.put(OrderStatus.COMPLETED, 
            Collections.singletonList(OrderStatus.EVALUATED));
        
        // 已取消 -> 无后续状态
        STATE_TRANSITIONS.put(OrderStatus.CANCELLED, Collections.emptyList());
        
        // 已评价 -> 无后续状态
        STATE_TRANSITIONS.put(OrderStatus.EVALUATED, Collections.emptyList());
    }
    
    /**
     * 验证状态流转是否合法
     * @param currentStatus 当前状态
     * @param targetStatus 目标状态
     * @return true-合法, false-非法
     */
    public boolean canTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        
        List<OrderStatus> allowedStatuses = STATE_TRANSITIONS.get(currentStatus);
        return allowedStatuses != null && allowedStatuses.contains(targetStatus);
    }
    
    /**
     * 验证状态流转，不合法则抛出异常
     * @param currentStatus 当前状态
     * @param targetStatus 目标状态
     */
    public void validateTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        if (!canTransition(currentStatus, targetStatus)) {
            String msg = String.format("订单状态流转非法: %s -> %s", 
                currentStatus.getDesc(), targetStatus.getDesc());
            log.error(msg);
            throw new BizIllegalException(msg);
        }
    }
    
    /**
     * 验证状态流转（使用Integer值）
     */
    public void validateTransition(Integer currentStatusValue, Integer targetStatusValue) {
        OrderStatus currentStatus = OrderStatus.of(currentStatusValue);
        OrderStatus targetStatus = OrderStatus.of(targetStatusValue);
        validateTransition(currentStatus, targetStatus);
    }
    
    /**
     * 获取当前状态允许流转到的状态列表
     */
    public List<OrderStatus> getAllowedTransitions(OrderStatus currentStatus) {
        return STATE_TRANSITIONS.getOrDefault(currentStatus, Collections.emptyList());
    }
}
