package com.hmall.item.service;

import com.hmall.api.dto.OrderDetailDTO;

import java.util.List;

/**
 * 库存服务接口
 */
public interface IItemStockService {
    
    /**
     * 扣减库存（Redis预扣减 + 数据库扣减）
     * @param items 商品列表
     */
    void deductStock(List<OrderDetailDTO> items);
    
    /**
     * 预热热门商品库存到Redis
     * @param itemId 商品ID
     */
    void warmUpStock(Long itemId);
    
    /**
     * 批量预热库存
     * @param itemIds 商品ID列表
     */
    void batchWarmUpStock(List<Long> itemIds);
}
