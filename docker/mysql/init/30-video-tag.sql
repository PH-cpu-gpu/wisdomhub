-- =============================================================================
-- 视频标签（B6.8）
-- 只存关联：标签名/分组以 content-service 的 tag 表为唯一真相，这里不冗余名字。
-- 已初始化过的卷必须手工执行本文件，init 目录只在空卷时自动跑。
-- =============================================================================

USE `wisdomhub_video`;

SET NAMES utf8mb4;

CREATE TABLE video_tag (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    video_id BIGINT NOT NULL COMMENT '视频 ID',
    tag_id BIGINT NOT NULL COMMENT 'content 标签 ID，不冗余名字',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_video_tag (video_id, tag_id),
    INDEX idx_tag_video (tag_id, video_id)
) ENGINE=InnoDB COMMENT='视频标签';
