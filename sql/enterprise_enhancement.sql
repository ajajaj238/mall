-- ========================================
-- 企业级功能增强 - 数据库脚本
-- ========================================

-- 1. 创建 Seata undo_log 表 (每个业务数据库都需要)
-- trade-service 数据库
CREATE TABLE IF NOT EXISTS `undo_log` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `branch_id` BIGINT(20) NOT NULL,
  `xid` VARCHAR(100) NOT NULL,
  `context` VARCHAR(128) NOT NULL,
  `rollback_info` LONGBLOB NOT NULL,
  `log_status` INT(11) NOT NULL,
  `log_created` DATETIME NOT NULL,
  `log_modified` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='Seata undo_log表';

-- item-service 数据库
-- 同上，在item数据库也创建 undo_log 表

-- user-service 数据库
-- 同上，在user数据库也创建 undo_log 表


-- 2. 创建本地消息表
CREATE TABLE IF NOT EXISTS `local_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `business_id` VARCHAR(64) NOT NULL COMMENT '业务ID',
  `business_type` VARCHAR(32) NOT NULL COMMENT '业务类型',
  `message_content` TEXT NOT NULL COMMENT '消息内容',
  `exchange_name` VARCHAR(64) NOT NULL COMMENT '交换机名称',
  `routing_key` VARCHAR(64) NOT NULL COMMENT '路由键',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0-待发送,1-已发送,2-发送失败',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `max_retry` INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
  `next_retry_time` DATETIME COMMENT '下次重试时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business` (`business_id`, `business_type`),
  KEY `idx_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表';


-- 3. 修改库存扣减SQL (使用 stock >= num 方式)
-- 已在 ItemMapper 中实现，这里提供参考
-- UPDATE item SET stock = stock - #{num} WHERE id = #{itemId} AND stock >= #{num}


-- 4. 订单幂等性Token表
CREATE TABLE IF NOT EXISTS `idempotent_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `token` VARCHAR(64) NOT NULL COMMENT '幂等性Token',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0-未使用,1-已使用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expire_time` DATETIME NOT NULL COMMENT '过期时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等性Token表';
