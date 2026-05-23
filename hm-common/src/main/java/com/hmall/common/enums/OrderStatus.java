package com.hmall.common.enums;

import lombok.Getter;

/**
 * 订单状态枚举
 */
@Getter
public enum OrderStatus {
    
    PENDING_PAYMENT(1, "待支付"),
    PAID(2, "已支付,未发货"),
    SHIPPED(3, "已发货,未确认"),
    COMPLETED(4, "确认收货,交易成功"),
    CANCELLED(5, "交易取消,订单关闭"),
    EVALUATED(6, "交易结束,已评价");
    
    private final Integer value;
    private final String desc;
    
    OrderStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }
    
    /**
     * 根据值获取枚举
     */
    public static OrderStatus of(Integer value) {
        if (value == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }
}
