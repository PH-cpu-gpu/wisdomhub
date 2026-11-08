-- =============================================================================
-- 视频服务：独立 schema 与媒体资产（B6.1 / B6.2）
-- 已初始化过的卷必须手工执行本文件，init 目录只在空卷时自动跑。
-- video / video_tag 在后续任务里追加（B6.5、B6.8）。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `wisdomhub_video`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

USE `wisdomhub_video`;

SET NAMES utf8mb4;

CREATE TABLE media_asset (
    id BIGINT PRIMARY KEY COMMENT '雪花 ID',
    owner_id BIGINT NOT NULL COMMENT '上传者 ID',
    object_key VARCHAR(255) NOT NULL COMMENT 'COS 对象键，服务端生成',
    file_size BIGINT NOT NULL COMMENT '文件大小（字节）；申请时为声明值，完成后回写实际值',
    mime_type VARCHAR(64) NOT NULL COMMENT 'MIME 类型，白名单内',
    duration_ms BIGINT DEFAULT NULL COMMENT '时长（毫秒），转码后回填',
    width INT DEFAULT NULL COMMENT '视频宽度，转码后回填',
    height INT DEFAULT NULL COMMENT '视频高度，转码后回填',
    cover_key VARCHAR(255) DEFAULT NULL COMMENT '封面对象键，转码后回填',
    status TINYINT NOT NULL COMMENT '1-待上传 2-已上传 3-转码中 4-失败 5-审核中 6-审核拒绝 7-就绪',
    fail_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
    mps_task_id VARCHAR(64) DEFAULT NULL COMMENT 'MPS 任务 ID',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_object_key (object_key),
    INDEX idx_owner_time (owner_id, create_time),
    INDEX idx_status_update (status, update_time)
) ENGINE=InnoDB COMMENT='媒体资产';
