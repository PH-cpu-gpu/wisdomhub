package com.wisdomhub.search.mq;

import com.rabbitmq.client.Channel;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.search.api.VideoFeign;
import com.wisdomhub.search.api.dto.InteractionCountVO;
import com.wisdomhub.search.api.dto.VideoInternalDTO;
import com.wisdomhub.search.api.dto.VideoTagDTO;
import com.wisdomhub.search.config.SearchMqConfiguration;
import com.wisdomhub.search.domain.document.VideoIndexDoc;
import com.wisdomhub.search.service.EsIndexService;
import com.wisdomhub.search.service.InteractionCountQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/** 消费 video.* 事件维护 video_index；payload 只提供 videoId，其余回查权威源。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoIndexConsumer {

    /** 与 video-service 的 VideoConstants.PUBLISHED 同值 */
    private static final int STATUS_PUBLISHED = 1;

    private final VideoFeign videoFeign;
    private final InteractionCountQueryService interactionCountQueryService;
    private final EsIndexService esIndexService;

    @RabbitListener(queues = SearchMqConfiguration.VIDEO_QUEUE)
    public void onVideoEvent(VideoEventEnvelope event, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(event);
        channel.basicAck(deliveryTag, false);
    }

    private void handle(VideoEventEnvelope event) {
        Long videoId = event.getPayload() == null ? null : event.getPayload().getVideoId();
        if (videoId == null) {
            throw new IllegalArgumentException("videoId 缺失, eventType=" + event.getEventType());
        }
        switch (event.getEventType()) {
            case MqRoutingKeyConstants.VIDEO_CREATED, MqRoutingKeyConstants.VIDEO_UPDATED ->
                    reloadAndIndex(videoId);
            case MqRoutingKeyConstants.VIDEO_DELETED -> esIndexService.deleteVideo(videoId);
            default -> log.debug("忽略不关心的路由键: {}", event.getEventType());
        }
    }

    private void reloadAndIndex(Long videoId) {
        VideoInternalDTO video;
        try {
            video = videoFeign.getVideo(videoId);
        } catch (BusinessException e) {
            if (e.getErrorCode().getCode() == CommonErrorCode.NOT_FOUND.getCode()) {
                log.info("视频已不存在，按删除处理, videoId={}", videoId);
                esIndexService.deleteVideo(videoId);
                return;
            }
            // 5xx / 超时：抛出去重试，不能把未确认状态的视频写进索引
            throw e;
        }
        if (video == null || !Integer.valueOf(STATUS_PUBLISHED).equals(video.getStatus())) {
            log.info("视频非已发布状态，按删除处理, videoId={}, status={}",
                    videoId, video == null ? null : video.getStatus());
            esIndexService.deleteVideo(videoId);
            return;
        }
        InteractionCountVO counts = interactionCountQueryService.get(
                ContentTargetTypeConstants.VIDEO, videoId);
        esIndexService.indexVideo(toDoc(video, counts));
    }

    private VideoIndexDoc toDoc(VideoInternalDTO video, InteractionCountVO counts) {
        List<VideoIndexDoc.Tag> tags = video.getTags() == null ? List.of()
                : video.getTags().stream().map(this::toTag).toList();
        return VideoIndexDoc.builder()
                .videoId(video.getVideoId())
                .title(video.getTitle())
                .suggest(video.getTitle())
                .summary(video.getSummary())
                .authorId(video.getAuthorId())
                .tags(tags)
                .coverKey(video.getCoverKey())
                .durationMs(video.getDurationMs())
                .status(video.getStatus())
                .createTime(video.getCreateTime())
                .publishTime(video.getPublishTime())
                .updateTime(video.getUpdateTime())
                .viewCount(video.getViewCount())
                .likeCount(counts == null ? 0 : nvl(counts.getLikeCount()))
                .commentCount(counts == null ? 0 : nvl(counts.getCommentCount()))
                .favoriteCount(counts == null ? 0 : nvl(counts.getFavoriteCount()))
                .build();
    }

    private VideoIndexDoc.Tag toTag(VideoTagDTO tag) {
        return VideoIndexDoc.Tag.builder()
                .tagId(tag.getTagId())
                .name(tag.getName())
                .category(tag.getCategory())
                .build();
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
