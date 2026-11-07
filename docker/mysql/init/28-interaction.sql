-- =============================================================================
-- 互动服务：评论、点赞、收藏与计数快照（B4.2）
-- 已初始化过的卷必须手工执行本文件，init 目录只在空卷时自动跑。
-- target_type 统一为 D2：1-文章 2-回答 3-问题 4-专栏 5-用户 6-标签提案 7-视频。
-- 计数只在本库维护；内容库的旧计数列在 B4.13 清场时删除。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `wisdomhub_interaction`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

USE `wisdomhub_interaction`;

SET NAMES utf8mb4;

CREATE TABLE comment (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    target_type TINYINT NOT NULL COMMENT '目标类型 1-文章 2-回答 3-问题 4-专栏 5-用户 6-标签提案 7-视频',
    target_id BIGINT NOT NULL COMMENT '目标 ID',
    user_id BIGINT NOT NULL COMMENT '评论者 ID',
    parent_id BIGINT DEFAULT NULL COMMENT '父评论 ID，二级回复用；一级评论为 NULL',
    content VARCHAR(1000) NOT NULL COMMENT '评论内容',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    INDEX idx_target (target_type, target_id, create_time),
    INDEX idx_user_id (user_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_deleted_update (deleted, update_time)
) ENGINE=InnoDB COMMENT='评论';

CREATE TABLE content_like (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    target_type TINYINT NOT NULL COMMENT '目标类型，同 comment.target_type',
    target_id BIGINT NOT NULL COMMENT '目标 ID',
    user_id BIGINT NOT NULL COMMENT '点赞用户 ID',
    create_time DATETIME NOT NULL COMMENT '点赞时间',
    UNIQUE KEY uk_target_user (target_type, target_id, user_id),
    INDEX idx_user_time (user_id, create_time)
) ENGINE=InnoDB COMMENT='点赞';

CREATE TABLE content_favorite (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    target_type TINYINT NOT NULL COMMENT '目标类型，同 comment.target_type',
    target_id BIGINT NOT NULL COMMENT '目标 ID',
    user_id BIGINT NOT NULL COMMENT '收藏用户 ID',
    create_time DATETIME NOT NULL COMMENT '收藏时间',
    UNIQUE KEY uk_target_user (target_type, target_id, user_id),
    INDEX idx_user_time (user_id, create_time)
) ENGINE=InnoDB COMMENT='收藏';

CREATE TABLE interaction_count (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    target_type TINYINT NOT NULL COMMENT '目标类型，同 comment.target_type',
    target_id BIGINT NOT NULL COMMENT '目标 ID',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    comment_count INT NOT NULL DEFAULT 0 COMMENT '评论数，含一级与二级',
    favorite_count INT NOT NULL DEFAULT 0 COMMENT '收藏数',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_target (target_type, target_id)
) ENGINE=InnoDB COMMENT='互动计数快照';
