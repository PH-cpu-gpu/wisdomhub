CREATE DATABASE IF NOT EXISTS `wisdomhub_user`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
USE `wisdomhub_user`;

SET NAMES utf8mb4;

CREATE TABLE user (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    username VARCHAR(20) NOT NULL COMMENT '用户名',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone CHAR(11) DEFAULT NULL COMMENT '手机号',
    password VARCHAR(60) NOT NULL COMMENT '密码 BCrypt 密文',
    avatar VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    bio VARCHAR(255) DEFAULT NULL COMMENT '用户简介',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    follow_count INT DEFAULT 0 NOT NULL COMMENT '关注数',
    fans_count INT DEFAULT 0 NOT NULL COMMENT '粉丝数',
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_phone (phone),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='用户';

CREATE TABLE user_follow (
                             id BIGINT PRIMARY KEY COMMENT '雪花 ID',
                             follower_id BIGINT NOT NULL COMMENT '关注者 ID',
                             followee_id BIGINT NOT NULL COMMENT '被关注者 ID',
                             create_time DATETIME NOT NULL COMMENT '创建时间',
                             UNIQUE KEY uk_follower_followee (follower_id, followee_id),
                             INDEX idx_followee_id (followee_id)
) ENGINE=InnoDB COMMENT='用户关注关系';

CREATE TABLE user_browse_history (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    article_id BIGINT NOT NULL COMMENT '文章 ID',
    create_time DATETIME NOT NULL COMMENT '首次浏览时间',
    update_time DATETIME NOT NULL COMMENT '最近浏览时间',
    UNIQUE KEY uk_user_article (user_id, article_id),
    INDEX idx_user_update (user_id, update_time),
    INDEX idx_article_id (article_id)
) ENGINE=InnoDB COMMENT='浏览历史';
