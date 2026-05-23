package com.hmall.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmall.common.domain.LocalMessage;
import com.hmall.common.mapper.LocalMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表服务
 * 保证消息可靠投递
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalMessageService {
    
    private final LocalMessageMapper messageMapper;
    private final RabbitTemplate rabbitTemplate;
    
    /**
     * 保存本地消息（与业务在同一事务）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMessage(String businessId, String businessType, String content, 
                           String exchangeName, String routingKey) {
        LocalMessage message = new LocalMessage();
        message.setBusinessId(businessId);
        message.setBusinessType(businessType);
        message.setMessageContent(content);
        message.setExchangeName(exchangeName);
        message.setRoutingKey(routingKey);
        message.setStatus(0);
        message.setRetryCount(0);
        message.setMaxRetry(3);
        message.setNextRetryTime(LocalDateTime.now());
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        messageMapper.insert(message);
        log.info("保存本地消息成功: businessId={}, businessType={}", businessId, businessType);
    }
    
    /**
     * 发送消息到MQ
     */
    public void sendMessage(LocalMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                message.getExchangeName(),
                message.getRoutingKey(),
                message.getMessageContent()
            );
            
            // 更新状态为已发送
            message.setStatus(1);
            messageMapper.updateById(message);
            log.info("发送消息成功: id={}, businessId={}", message.getId(), message.getBusinessId());
            
        } catch (Exception e) {
            log.error("发送消息失败: id={}, businessId={}", message.getId(), message.getBusinessId(), e);
            
            // 更新重试信息
            message.setRetryCount(message.getRetryCount() + 1);
            message.setNextRetryTime(LocalDateTime.now().plusMinutes(5));
            
            if (message.getRetryCount() >= message.getMaxRetry()) {
                message.setStatus(2); // 发送失败
                log.error("消息发送失败，已达最大重试次数: id={}", message.getId());
            }
            messageMapper.updateById(message);
        }
    }
    
    /**
     * 定时扫描待发送消息
     * 每30秒执行一次
     */
    @Scheduled(fixedDelay = 30000)
    public void scanPendingMessages() {
        List<LocalMessage> messages = messageMapper.selectList(
            new LambdaQueryWrapper<LocalMessage>()
                .eq(LocalMessage::getStatus, 0)
                .le(LocalMessage::getNextRetryTime, LocalDateTime.now())
                .lt(LocalMessage::getRetryCount, 3)
        );
        
        if (!messages.isEmpty()) {
            log.info("扫描到待发送消息: count={}", messages.size());
            for (LocalMessage message : messages) {
                sendMessage(message);
            }
        }
    }
}
