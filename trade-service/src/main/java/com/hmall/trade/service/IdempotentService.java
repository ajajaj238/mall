package com.hmall.trade.service;

import cn.hutool.core.lang.UUID;
import com.hmall.common.exception.BizIllegalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 订单幂等性服务
 * 基于Redis Token机制防止重复下单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentService {
    
    private final StringRedisTemplate redisTemplate;
    
    private static final String TOKEN_PREFIX = "order:token:";
    private static final long TOKEN_EXPIRE_MINUTES = 5;
    
    /**
     * 生成幂等性Token
     * @param userId 用户ID
     * @return Token字符串
     */
    public String generateToken(Long userId) {
        String token = UUID.randomUUID().toString(true);
        String key = TOKEN_PREFIX + token;
        
        // 存入Redis，5分钟过期
        redisTemplate.opsForValue().set(key, userId.toString(), TOKEN_EXPIRE_MINUTES, TimeUnit.MINUTES);
        
        log.info("生成幂等性Token: userId={}, token={}", userId, token);
        return token;
    }
    
    /**
     * 验证并消费Token
     * @param token Token字符串
     * @param userId 用户ID
     * @return true-验证成功, false-验证失败
     */
    public boolean validateAndConsumeToken(String token, Long userId) {
        if (token == null || token.isEmpty()) {
            log.warn("Token为空: userId={}", userId);
            return false;
        }
        
        String key = TOKEN_PREFIX + token;
        String storedUserId = redisTemplate.opsForValue().get(key);
        
        // Token不存在或已被使用
        if (storedUserId == null) {
            log.warn("Token不存在或已被使用: token={}, userId={}, key={}", token, userId, key);
            return false;
        }
        
        // 验证用户ID
        if (!storedUserId.equals(userId.toString())) {
            log.warn("Token用户ID不匹配: token={}, expected={}, actual={}", token, userId, storedUserId);
            return false;
        }
        
        // 删除Token（消费）
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Token验证成功并已消费: userId={}, token={}", userId, token);
            return true;
        }
        
        log.warn("Token删除失败: token={}, userId={}", token, userId);
        return false;
    }
    
    /**
     * 验证Token，失败则抛出异常
     */
    public void validateAndConsumeTokenOrThrow(String token, Long userId) {
        if (!validateAndConsumeToken(token, userId)) {
            throw new BizIllegalException("订单提交失败，请刷新页面重试");
        }
    }
}
