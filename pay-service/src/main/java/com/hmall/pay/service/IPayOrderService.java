package com.hmall.pay.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmall.pay.domin.dto.PayApplyDTO;
import com.hmall.pay.domin.dto.PayOrderFormDTO;
import com.hmall.pay.domin.po.PayOrder;


/**
 * <p>
 * 支付订单 服务类
 * </p>
 *
 * @author zhj
 */
public interface IPayOrderService extends IService<PayOrder> {

    String applyPayOrder(PayApplyDTO applyDTO);

    void tryPayOrderByBalance(PayOrderFormDTO payOrderFormDTO);

    PayOrder queryPayOrderByBizOrderNo(Long bizOrderNo);

    void closePayOrderByBizOrderNo(Long bizOrderNo);
}
