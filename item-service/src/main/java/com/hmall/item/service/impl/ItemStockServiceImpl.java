package com.hmall.item.service.impl;

import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.item.constants.StockMQConstants;
import com.hmall.item.domin.dto.StockDeductMessage;
import com.hmall.item.domin.po.Item;
import com.hmall.item.mapper.ItemMapper;
import com.hmall.item.service.IItemStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存服务实现
 * 热门商品走Redis Lua预扣减和MQ异步扣库，未预热商品直接扣减MySQL库存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemStockServiceImpl implements IItemStockService {
    
    private final ItemMapper itemMapper;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    
    private static final String STOCK_KEY_PREFIX = "item:stock:";
    private static final Long STOCK_DEDUCT_SUCCESS = 1L;
    private static final Long STOCK_NOT_WARMED_UP = -1L;
    private static final Long STOCK_NOT_ENOUGH = 0L;
    private static final DefaultRedisScript<Long> STOCK_DEDUCT_SCRIPT;

    static {
        STOCK_DEDUCT_SCRIPT = new DefaultRedisScript<>();
        STOCK_DEDUCT_SCRIPT.setResultType(Long.class);
        STOCK_DEDUCT_SCRIPT.setScriptText(
                "for i = 1, #KEYS do " +
                        "local stock = tonumber(redis.call('GET', KEYS[i])); " +
                        "local num = tonumber(ARGV[i]); " +
                        "if stock == nil then return -1; end; " +
                        "if stock < num then return 0; end; " +
                        "end; " +
                        "for i = 1, #KEYS do " +
                        "redis.call('DECRBY', KEYS[i], ARGV[i]); " +
                        "end; " +
                        "return 1;"
        );
    }
    
    /**
     * 扣减库存：有Redis库存的商品走Lua预扣减，没有Redis库存的商品走MySQL同步扣减
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(List<OrderDetailDTO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<OrderDetailDTO> redisItems = new ArrayList<>();
        List<OrderDetailDTO> databaseItems = new ArrayList<>();
        for (OrderDetailDTO item : items) {
            String key = STOCK_KEY_PREFIX + item.getItemId();
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                redisItems.add(item);
            } else {
                databaseItems.add(item);
            }
        }

        deductStockFromDatabase(databaseItems);

        if (redisItems.isEmpty()) {
            return;
        }

        List<String> keys = redisItems.stream()
                .map(item -> STOCK_KEY_PREFIX + item.getItemId())
                .collect(Collectors.toList());
        List<String> nums = redisItems.stream()
                .map(item -> String.valueOf(item.getNum()))
                .collect(Collectors.toList());

        Long result = redisTemplate.execute(STOCK_DEDUCT_SCRIPT, keys, nums.toArray());
        if (STOCK_NOT_WARMED_UP.equals(result)) {
            throw new BizIllegalException("Redis库存不存在，请重试");
        }
        if (STOCK_NOT_ENOUGH.equals(result)) {
            throw new BizIllegalException("库存不足");
        }
        if (!STOCK_DEDUCT_SUCCESS.equals(result)) {
            throw new BizIllegalException("库存扣减失败");
        }

        try {
            rabbitTemplate.convertAndSend(
                    StockMQConstants.STOCK_EXCHANGE_NAME,
                    StockMQConstants.STOCK_DEDUCT_KEY,
                    new StockDeductMessage(redisItems)
            );
            log.info("Redis预扣减成功，已发送库存异步扣减任务: items={}", redisItems);
        } catch (Exception e) {
            restoreRedisStock(redisItems);
            log.error("库存扣减任务发送失败，已恢复Redis库存: items={}", redisItems, e);
            throw new BizIllegalException("库存扣减失败，请稍后重试");
        }
    }

    private void deductStockFromDatabase(List<OrderDetailDTO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (OrderDetailDTO item : items) {
            int count = itemMapper.updateStock(item);
            if (count == 0) {
                log.warn("MySQL库存不足: itemId={}, num={}", item.getItemId(), item.getNum());
                throw new BizIllegalException("库存不足: itemId=" + item.getItemId());
            }
            log.info("MySQL同步扣减库存成功: itemId={}, num={}", item.getItemId(), item.getNum());
        }
    }

    private void restoreRedisStock(List<OrderDetailDTO> items) {
        List<Object> results = new ArrayList<>(items.size());
        for (OrderDetailDTO item : items) {
            String key = STOCK_KEY_PREFIX + item.getItemId();
            results.add(redisTemplate.opsForValue().increment(key, item.getNum()));
        }
        log.info("Redis库存恢复完成: results={}", results);
    }
    
    /**
     * 预热单个商品库存到Redis
     */
    @Override
    public void warmUpStock(Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizIllegalException("商品不存在: itemId=" + itemId);
        }
        
        String key = STOCK_KEY_PREFIX + itemId;
        redisTemplate.opsForValue().set(key, String.valueOf(item.getStock()));
        log.info("库存预热成功: itemId={}, stock={}", itemId, item.getStock());
    }
    
    /**
     * 批量预热库存
     */
    @Override
    public void batchWarmUpStock(List<Long> itemIds) {
        for (Long itemId : itemIds) {
            try {
                warmUpStock(itemId);
            } catch (Exception e) {
                log.error("库存预热失败: itemId={}", itemId, e);
            }
        }
    }
}
