package com.hmall.item.service.impl;

import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.item.domin.po.Item;
import com.hmall.item.mapper.ItemMapper;
import com.hmall.item.service.IItemStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 库存服务实现
 * 热门商品使用Redis预扣减，普通商品直接数据库扣减
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemStockServiceImpl implements IItemStockService {
    
    private final ItemMapper itemMapper;
    private final StringRedisTemplate redisTemplate;
    
    private static final String STOCK_KEY_PREFIX = "item:stock:";
    
    /**
     * 扣减库存
     * 策略：先尝试Redis预扣减，失败则直接数据库扣减
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(List<OrderDetailDTO> items) {
        for (OrderDetailDTO item : items) {
            boolean success = deductStockForItem(item);
            if (!success) {
                throw new BizIllegalException("库存不足: itemId=" + item.getItemId());
            }
        }
    }
    
    /**
     * 扣减单个商品库存
     */
    private boolean deductStockForItem(OrderDetailDTO item) {
        String key = STOCK_KEY_PREFIX + item.getItemId();
        
        // 1. 尝试Redis预扣减（热门商品）
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return deductStockFromRedis(item, key);
        }
        
        // 2. 直接数据库扣减（普通商品）
        return deductStockFromDatabase(item);
    }
    
    /**
     * Redis预扣减
     */
    private boolean deductStockFromRedis(OrderDetailDTO item, String key) {
        try {
            // Redis原子扣减
            Long remainStock = redisTemplate.opsForValue().decrement(key, item.getNum());
            
            if (remainStock == null || remainStock < 0) {
                // 库存不足，恢复Redis
                redisTemplate.opsForValue().increment(key, item.getNum());
                log.warn("Redis库存不足: itemId={}, num={}", item.getItemId(), item.getNum());
                return false;
            }
            
            // 数据库扣减
            boolean dbSuccess = deductStockFromDatabase(item);
            if (!dbSuccess) {
                // 数据库扣减失败，恢复Redis
                redisTemplate.opsForValue().increment(key, item.getNum());
                log.error("数据库扣减失败，已恢复Redis: itemId={}", item.getItemId());
                return false;
            }
            
            log.info("Redis预扣减成功: itemId={}, num={}, remainStock={}", 
                item.getItemId(), item.getNum(), remainStock);
            return true;
            
        } catch (Exception e) {
            log.error("Redis扣减异常: itemId={}", item.getItemId(), e);
            // Redis异常，降级到数据库扣减
            return deductStockFromDatabase(item);
        }
    }
    
    /**
     * 数据库扣减（使用 stock >= num 方式）
     */
    private boolean deductStockFromDatabase(OrderDetailDTO item) {
        try {
            itemMapper.updateStock(item);
            log.info("数据库扣减成功: itemId={}, num={}", item.getItemId(), item.getNum());
            return true;
        } catch (Exception e) {
            log.error("数据库扣减失败: itemId={}, num={}", item.getItemId(), item.getNum(), e);
            return false;
        }
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
