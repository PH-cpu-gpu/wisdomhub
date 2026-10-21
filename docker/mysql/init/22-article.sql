CREATE DATABASE IF NOT EXISTS `wisdomhub_content`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
USE `wisdomhub_content`;

SET NAMES utf8mb4;

CREATE TABLE article (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    author_id BIGINT NOT NULL COMMENT '作者 ID',
    title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '文章标题',
    summary VARCHAR(255) NOT NULL DEFAULT '' COMMENT '文章摘要',
    state TINYINT NOT NULL COMMENT '文章状态 1-草稿 2-已发布 3-下架',
    view_count INT NOT NULL DEFAULT 0 COMMENT '浏览量',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    comment_count INT NOT NULL DEFAULT 0 COMMENT '评论数',
    favorite_count INT NOT NULL DEFAULT 0 COMMENT '收藏数',
    publish_time DATETIME DEFAULT NULL COMMENT '发布时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，只有覆盖发布递增',
    INDEX idx_author_state (author_id, state, create_time),
    INDEX idx_author_state_update (author_id, state, update_time),
    INDEX idx_state_publish (state, publish_time),
    INDEX idx_state_view (state, view_count),
    -- B2.1 内部增量同步按 (update_time, id) 复合游标扫；缺它这条查询要全表扫 + filesort
    INDEX idx_update_time_id (update_time, id),
    -- B2.32 墓碑清理：WHERE deleted=1 AND update_time < ? ORDER BY update_time, id
    INDEX idx_deleted_update (deleted, update_time)
) ENGINE=InnoDB COMMENT='文章';