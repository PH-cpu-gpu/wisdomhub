CREATE DATABASE IF NOT EXISTS `wisdomhub_content`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
USE `wisdomhub_content`;

SET NAMES utf8mb4;

CREATE TABLE question (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    user_id BIGINT NOT NULL COMMENT '提问者 ID',
    title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '问题标题',
    content TEXT NOT NULL COMMENT '问题正文',
    answer_count INT NOT NULL DEFAULT 0 COMMENT '回答数',
    favorite_count INT NOT NULL DEFAULT 0 COMMENT '收藏数',
    accepted_answer_id BIGINT DEFAULT NULL COMMENT '采纳的回答 ID',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='问题';

CREATE TABLE answer (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    question_id BIGINT NOT NULL COMMENT '问题 ID',
    user_id BIGINT NOT NULL COMMENT '回答者 ID',
    content TEXT NOT NULL COMMENT '回答正文',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    comment_count INT NOT NULL DEFAULT 0 COMMENT '评论数',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    INDEX idx_question_id (question_id, create_time),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='回答';

CREATE TABLE answer_like (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    answer_id BIGINT NOT NULL COMMENT '回答 ID',
    create_time DATETIME NOT NULL COMMENT '点赞时间',
    UNIQUE KEY uk_user_answer (user_id, answer_id),
    INDEX idx_answer_id (answer_id)
) ENGINE=InnoDB COMMENT='回答点赞';

CREATE TABLE question_favorite (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    question_id BIGINT NOT NULL COMMENT '问题 ID',
    create_time DATETIME NOT NULL COMMENT '收藏时间',
    UNIQUE KEY uk_user_question (user_id, question_id),
    INDEX idx_question_id (question_id)
) ENGINE=InnoDB COMMENT='问题收藏';
