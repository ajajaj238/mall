package com.hmall.item.listener;

import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.item.constants.StockMQConstants;
import com.hmall.item.domin.dto.StockDeductMessage;
import com.hmall.item.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockDeductListener {

    private final ItemMapper itemMapper;

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = StockMQConstants.STOCK_DEDUCT_QUEUE_NAME, durable = "true"),
            exchange = @Exchange(name = StockMQConstants.STOCK_EXCHANGE_NAME),
            key = StockMQConstants.STOCK_DEDUCT_KEY
    ))
    public void listenStockDeduct(StockDeductMessage message) {
        for (OrderDetailDTO detail : message.getDetails()) {
            int count = itemMapper.updateStock(detail);
            if (count == 0) {
                log.error("异步扣减MySQL库存失败: itemId={}, num={}", detail.getItemId(), detail.getNum());
                throw new BizIllegalException("库存扣减失败: itemId=" + detail.getItemId());
            }
            log.info("异步扣减MySQL库存成功: itemId={}, num={}", detail.getItemId(), detail.getNum());
        }
    }
}
