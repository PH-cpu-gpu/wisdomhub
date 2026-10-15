CREATE DATABASE IF NOT EXISTS `wisdomhub_notification`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
USE `wisdomhub_notification`;

SET NAMES utf8mb4;

CREATE TABLE notification (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    user_id BIGINT NOT NULL COMMENT '接收者 ID',
    sender_id BIGINT DEFAULT NULL COMMENT '触发者 ID，系统通知为 NULL',
    type TINYINT NOT NULL COMMENT '类型 1-点赞 2-评论 3-回答 4-关注 5-专栏购买 6-AI 分析完成 7-系统通知',
    target_type TINYINT DEFAULT NULL COMMENT '目标类型 1-文章 2-回答 3-问题 4-专栏 5-用户，系统通知为 NULL；1/2 与 comment.target_type 同值',
    target_id BIGINT DEFAULT NULL COMMENT '目标 ID',
    content VARCHAR(500) NOT NULL DEFAULT '' COMMENT '通知正文，只有系统通知会填，其余类型由前端按 type 拼文案',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '已读 0-未读 1-已读',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间，通知只被改一次（标已读），即已读时间',
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_user_read (user_id, is_read)
) ENGINE=InnoDB COMMENT='站内通知';

CREATE TABLE notification_setting (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    type TINYINT NOT NULL COMMENT '类型，取值同 notification.type 的 1~6，系统通知不可关故不在此表',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否接收 0-关闭 1-开启',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_user_type (user_id, type)
) ENGINE=InnoDB COMMENT='通知偏好设置';
