package com.hmall.common.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 本地消息表实体
 */
@Data
@TableName("local_message")
public class LocalMessage {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 业务ID
     */
    private String businessId;
    
    /**
     * 业务类型
     */
    private String businessType;
    
    /**
     * 消息内容
     */
    private String messageContent;
    
    /**
     * 交换机名称
     */
    private String exchangeName;
    
    /**
     * 路由键
     */
    private String routingKey;
    
    /**
     * 状态: 0-待发送, 1-已发送, 2-发送失败
     */
    private Integer status;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetry;
    
    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}
