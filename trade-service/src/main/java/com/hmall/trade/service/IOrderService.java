package com.hmall.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmall.trade.domin.dto.OrderFormDTO;
import com.hmall.trade.domin.po.Order;


/**
 * <p>
 *  服务类
 * </p>
 *
 * @author zhj
 */
public interface IOrderService extends IService<Order> {

    Long createOrder(OrderFormDTO orderFormDTO);

    void markOrderPaySuccess(Long orderId);

    void cancelOrder(Long orderId);
}
