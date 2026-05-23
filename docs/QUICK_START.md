# 快速启动指南

## 🎯 前置条件

确保以下服务已启动：

- [x] MySQL (端口3306)
- [x] Redis (端口6379)
- [x] Nacos (端口8848)
- [x] Seata Server (端口8091)
- [x] RabbitMQ (端口5672)

---

## 📝 步骤1: 执行数据库脚本

```bash
# 在MySQL中执行
mysql -u root -p < sql/enterprise_enhancement.sql
```

或手动在各数据库执行：
- `hm-trade` 数据库: 创建 `undo_log` 和 `local_message` 表
- `hm-item` 数据库: 创建 `undo_log` 表
- `hm-user` 数据库: 创建 `undo_log` 表

---

## 🚀 步骤2: 启动服务

### 方式1: IDEA启动

1. 启动 `item-service` (ItemApplication)
2. 启动 `user-service` (UserApplication)
3. 启动 `trade-service` (tradeApplication)

### 方式2: Maven启动

```bash
# 启动item-service
cd item-service
mvn spring-boot:run

# 启动user-service
cd user-service
mvn spring-boot:run

# 启动trade-service
cd trade-service
mvn spring-boot:run
```

---

## ✅ 步骤3: 验证功能

### 3.1 验证幂等性Token生成

```bash
curl http://localhost:8085/orders/token
# 返回: 类似 "a1b2c3d4e5f6..."
```

### 3.2 验证库存预热

```bash
# 预热商品ID为1的库存
curl -X POST http://localhost:8081/items/stock/warmup/1

# 查看Redis
redis-cli
> GET item:stock:1
"100"  # 显示库存数量
```

### 3.3 验证下单流程

```bash
# 1. 获取Token
TOKEN=$(curl -s http://localhost:8085/orders/token)

# 2. 创建订单
curl -X POST http://localhost:8085/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "addressId": 1,
    "paymentType": 1,
    "details": [
      {"itemId": 1, "num": 2}
    ],
    "idempotentToken": "'$TOKEN'"
  }'
```

### 3.4 验证本地消息表

```sql
-- 查看本地消息
SELECT * FROM hm_trade.local_message;

-- 查看待发送消息
SELECT * FROM hm_trade.local_message WHERE status = 0;
```

### 3.5 验证Seata分布式事务

```sql
-- 查看undo_log（事务执行中会有记录）
SELECT * FROM hm_trade.undo_log;
SELECT * FROM hm_item.undo_log;
```

---

## 🔧 常见问题

### Q1: Seata连接失败

**现象**: 启动时报错 `can not connect to seata server`

**解决**:
```bash
# 1. 检查Seata Server是否启动
netstat -an | grep 8091

# 2. 检查Nacos中是否注册了seata-server
curl http://localhost:8848/nacos/v1/ns/instance/list?serviceName=seata-server
```

### Q2: Redis连接失败

**现象**: 启动时报错 `Unable to connect to Redis`

**解决**:
```bash
# 检查Redis是否启动
redis-cli ping
# 应返回: PONG
```

### Q3: 本地消息表定时任务不执行

**现象**: 消息一直是待发送状态

**解决**:
- 检查是否添加了 `@EnableScheduling` 注解
- 查看日志是否有定时任务执行记录
- 确认 `LocalMessageService` 被Spring管理

### Q4: 幂等性Token验证失败

**现象**: 下单时提示"订单提交失败，请刷新页面重试"

**解决**:
- Token有效期5分钟，检查是否过期
- Token只能使用一次，不要重复提交
- 检查Redis中是否存在Token: `redis-cli GET order:token:xxx`

---

## 📊 监控检查

### 检查Seata事务

```bash
# 查看Seata控制台
http://localhost:7091
```

### 检查Nacos服务注册

```bash
# 查看服务列表
http://localhost:8848/nacos
```

### 检查Redis数据

```bash
redis-cli
> KEYS item:stock:*    # 查看库存缓存
> KEYS order:token:*   # 查看Token
```

### 检查RabbitMQ消息

```bash
# 访问RabbitMQ管理界面
http://localhost:15672
# 用户名: admin
# 密码: zhj123456
```

---

## 🎉 测试场景

### 场景1: 正常下单

1. 获取Token
2. 提交订单
3. 检查订单状态
4. 检查库存是否扣减
5. 检查本地消息是否发送

### 场景2: 重复提交（幂等性）

1. 获取Token
2. 第一次提交订单 ✅
3. 使用相同Token再次提交 ❌ (应该失败)

### 场景3: 库存不足

1. 预热商品库存为1
2. 提交购买数量为2的订单 ❌ (应该失败)
3. 检查Redis库存未扣减

### 场景4: 分布式事务回滚

1. 模拟库存扣减失败
2. 检查订单是否回滚
3. 检查undo_log记录

---

## 📞 技术支持

如遇问题，请查看日志：

```bash
# trade-service日志
tail -f trade-service/logs/spring.log

# item-service日志
tail -f item-service/logs/spring.log
```

或查看详细文档: `docs/ENTERPRISE_ENHANCEMENT.md`
