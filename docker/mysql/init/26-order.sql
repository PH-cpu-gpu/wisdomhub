CREATE DATABASE IF NOT EXISTS `wisdomhub_order`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
USE `wisdomhub_order`;

SET NAMES utf8mb4;

CREATE TABLE `order` (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    order_no VARCHAR(32) NOT NULL COMMENT '订单号，yyyyMMddHHmmss + 6 位序列',
    user_id BIGINT NOT NULL COMMENT '买家 ID',
    amount INT NOT NULL COMMENT '订单金额，单位为分',
    state TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0-待支付 1-已支付 2-已取消 3-退款中 4-已退款',
    expire_time DATETIME NOT NULL COMMENT '支付截止时间',
    paid_time DATETIME DEFAULT NULL COMMENT '支付时间',
    refund_time DATETIME DEFAULT NULL COMMENT '退款完成时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间，取消时即取消时间',
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_state_expire (state, expire_time)
) ENGINE=InnoDB COMMENT='订单';

CREATE TABLE order_item (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    order_id BIGINT NOT NULL COMMENT '订单 ID',
    item_type TINYINT NOT NULL COMMENT '商品类型 1-专栏',
    item_id BIGINT NOT NULL COMMENT '商品 ID',
    author_id BIGINT NOT NULL COMMENT '作者 ID 快照，支付事件通知用',
    title VARCHAR(100) NOT NULL COMMENT '标题快照，专栏改名/删除后订单仍可读',
    price INT NOT NULL COMMENT '成交单价，单位为分',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_order_item (order_id, item_type, item_id)
) ENGINE=InnoDB COMMENT='订单明细';

CREATE TABLE payment_record (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    payment_no VARCHAR(32) NOT NULL COMMENT '支付单号，渠道 out_trade_no',
    order_id BIGINT NOT NULL COMMENT '订单 ID',
    amount INT NOT NULL COMMENT '支付金额，单位为分',
    pay_type TINYINT NOT NULL COMMENT '支付方式 1-模拟支付 2-微信支付 3-支付宝支付',
    state TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0-待支付 1-成功 2-失败 3-已关闭',
    prepay_id VARCHAR(128) DEFAULT NULL COMMENT '微信 prepay_id，其他渠道为 NULL',
    transaction_id VARCHAR(64) DEFAULT NULL COMMENT '渠道交易号，支付成功后由渠道返回',
    expire_time DATETIME DEFAULT NULL COMMENT '支付单过期时间',
    paid_time DATETIME DEFAULT NULL COMMENT '支付成功时间',
    close_time DATETIME DEFAULT NULL COMMENT '关闭时间',
    refund_no VARCHAR(64) DEFAULT NULL COMMENT '退款单号',
    refund_amount INT NOT NULL DEFAULT 0 COMMENT '退款金额，单位为分',
    refund_state TINYINT NOT NULL DEFAULT 0 COMMENT '退款状态 0-无 1-处理中 2-成功 3-失败',
    channel_refund_id VARCHAR(64) DEFAULT NULL COMMENT '渠道退款单号',
    refund_time DATETIME DEFAULT NULL COMMENT '退款成功时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_transaction (pay_type, transaction_id),
    UNIQUE KEY uk_refund_no (refund_no),
    INDEX idx_order_id (order_id),
    INDEX idx_state_expire (state, expire_time)
) ENGINE=InnoDB COMMENT='支付流水';
