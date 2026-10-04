CREATE DATABASE IF NOT EXISTS `wisdomhub_content`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
USE `wisdomhub_content`;

SET NAMES utf8mb4;

CREATE TABLE tag (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    name VARCHAR(50) NOT NULL COMMENT '标签名，对应图谱 Tag.name',
    description VARCHAR(255) NOT NULL DEFAULT '' COMMENT '简介',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB COMMENT='标签';

CREATE TABLE article_tag (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    article_id BIGINT NOT NULL COMMENT '文章 ID',
    tag_id BIGINT NOT NULL COMMENT '标签 ID',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_article_tag (article_id, tag_id),
    INDEX idx_tag_id (tag_id)
) ENGINE=InnoDB COMMENT='文章标签';

CREATE TABLE comment (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    target_type TINYINT NOT NULL COMMENT '目标类型 1-文章 2-回答',
    target_id BIGINT NOT NULL COMMENT '目标 ID',
    user_id BIGINT NOT NULL COMMENT '评论者 ID',
    parent_id BIGINT DEFAULT NULL COMMENT '父评论 ID，二级回复用；一级评论为 NULL',
    content VARCHAR(1000) NOT NULL COMMENT '评论内容',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    INDEX idx_target (target_type, target_id, create_time),
    INDEX idx_user_id (user_id),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB COMMENT='评论';

CREATE TABLE article_like (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    article_id BIGINT NOT NULL COMMENT '文章 ID',
    create_time DATETIME NOT NULL COMMENT '点赞时间',
    UNIQUE KEY uk_user_article (user_id, article_id),
    INDEX idx_article_id (article_id)
) ENGINE=InnoDB COMMENT='文章点赞';

CREATE TABLE article_favorite (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    article_id BIGINT NOT NULL COMMENT '文章 ID',
    create_time DATETIME NOT NULL COMMENT '收藏时间',
    UNIQUE KEY uk_user_article (user_id, article_id),
    INDEX idx_article_id (article_id)
) ENGINE=InnoDB COMMENT='文章收藏';

CREATE TABLE `column` (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    user_id BIGINT NOT NULL COMMENT '创建者 ID',
    title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '专栏名',
    description VARCHAR(500) NOT NULL DEFAULT '' COMMENT '简介',
    cover VARCHAR(512) DEFAULT NULL COMMENT '封面 URL',
    price INT NOT NULL DEFAULT 0 COMMENT '价格，单位为分，0 表示免费',
    article_count INT NOT NULL DEFAULT 0 COMMENT '收录文章数',
    state TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1-正常 2-下架',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='专栏';

CREATE TABLE column_article (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    column_id BIGINT NOT NULL COMMENT '专栏 ID',
    article_id BIGINT NOT NULL COMMENT '文章 ID',
    create_time DATETIME NOT NULL COMMENT '加入时间',
    UNIQUE KEY uk_column_article (column_id, article_id),
    INDEX idx_article_id (article_id)
) ENGINE=InnoDB COMMENT='专栏文章';

CREATE TABLE column_purchase (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    user_id BIGINT NOT NULL COMMENT '购买者 ID',
    column_id BIGINT NOT NULL COMMENT '专栏 ID',
    order_id BIGINT NOT NULL COMMENT '订单 ID',
    price INT NOT NULL COMMENT '成交价，单位为分',
    create_time DATETIME NOT NULL COMMENT '购买时间',
    UNIQUE KEY uk_user_column (user_id, column_id),
    INDEX idx_column_id (column_id),
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB COMMENT='专栏购买记录';
