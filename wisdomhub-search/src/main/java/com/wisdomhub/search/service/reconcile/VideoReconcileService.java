package com.wisdomhub.search.service.reconcile;

import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.mq.event.VideoDeletedEvent;
import com.wisdomhub.common.mq.event.VideoUpdatedEvent;
import com.wisdomhub.common.mq.event.payload.VideoDeletedPayload;
import com.wisdomhub.common.mq.event.payload.VideoEventPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.search.api.VideoFeign;
import com.wisdomhub.search.api.dto.InteractionCountVO;
import com.wisdomhub.search.api.dto.VideoInternalDTO;
import com.wisdomhub.search.config.ReconcileProperties;
import com.wisdomhub.search.domain.document.VideoIndexDoc;
import com.wisdomhub.search.service.EsIndexService;
import com.wisdomhub.search.service.InteractionCountQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * video_index 对账：以 video 表为准，不一致就重投 video.updated / video.deleted。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoReconcileService {

    /** 与 video-service 的 VideoConstants.PUBLISHED 同值 */
    private static final int STATUS_PUBLISHED = 1;

    private static final String EPOCH = "1970-01-01T00:00:00";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final VideoFeign videoFeign;
    private final InteractionCountQueryService interactionCountQueryService;
    private final EsIndexService esIndexService;
    private final EventPublisher eventPublisher;
    private final ReconcileProperties properties;

    public ReconcileResult reconcile(boolean full) {
        ReconcileResult result = new ReconcileResult();
        Set<Long> sourceIds = new HashSet<>();
        String since = full
                ? EPOCH
                : LocalDateTime.now(ZoneOffset.UTC).minusMinutes(properties.getLookbackMinutes()).format(ISO);
        long lastId = 0L;

        while (true) {
            List<VideoInternalDTO> page = videoFeign.syncVideos(since, lastId, properties.getPageSize());
            if (page.isEmpty()) {
                break;
            }
            result.setScanned(result.getScanned() + page.size());
            List<Long> ids = page.stream().map(VideoInternalDTO::getVideoId).filter(Objects::nonNull).toList();
            Map<Long, VideoIndexDoc> docs = esIndexService.mgetVideos(ids);
            Map<Long, InteractionCountVO> counts = interactionCountQueryService.byTargetIds(
                    ContentTargetTypeConstants.VIDEO, ids);
            for (VideoInternalDTO video : page) {
                if (video.getVideoId() == null) {
                    continue;
                }
                applyCounts(video, counts.get(video.getVideoId()));
                sourceIds.add(video.getVideoId());
                compareAndPublish(video, docs.get(video.getVideoId()), result);
            }
            VideoInternalDTO last = page.getLast();
            if (last.getUpdateTime() == null) {
                log.warn("视频 update_time 为空，停止本页推进: videoId={}", last.getVideoId());
                break;
            }
            since = last.getUpdateTime().format(ISO);
            lastId = last.getVideoId();
            if (page.size() < properties.getPageSize()) {
                break;
            }
        }

        if (full) {
            scanOrphans(sourceIds, result);
        }
        return result;
    }

    private void compareAndPublish(VideoInternalDTO video, VideoIndexDoc doc, ReconcileResult result) {
        if (video.getStatus() == null || video.getStatus() != STATUS_PUBLISHED) {
            if (doc != null) {
                result.setMismatched(result.getMismatched() + 1);
                publishUpdated(video, result);
            }
            return;
        }
        if (doc == null || !sameVideo(video, doc)) {
            result.setMismatched(result.getMismatched() + 1);
            publishUpdated(video, result);
        }
    }

    private void applyCounts(VideoInternalDTO video, InteractionCountVO counts) {
        video.setLikeCount(counts == null ? 0 : nvl(counts.getLikeCount()));
        video.setCommentCount(counts == null ? 0 : nvl(counts.getCommentCount()));
        video.setFavoriteCount(counts == null ? 0 : nvl(counts.getFavoriteCount()));
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean sameVideo(VideoInternalDTO source, VideoIndexDoc doc) {
        return sameTime(source.getUpdateTime(), doc.getUpdateTime())
                && Objects.equals(source.getViewCount(), doc.getViewCount())
                && Objects.equals(source.getLikeCount(), doc.getLikeCount())
                && Objects.equals(source.getCommentCount(), doc.getCommentCount())
                && Objects.equals(source.getFavoriteCount(), doc.getFavoriteCount())
                && Objects.equals(source.getStatus(), doc.getStatus());
    }

    private boolean sameTime(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.truncatedTo(ChronoUnit.SECONDS).equals(right.truncatedTo(ChronoUnit.SECONDS));
    }

    private void publishUpdated(VideoInternalDTO video, ReconcileResult result) {
        eventPublisher.send(new VideoUpdatedEvent(VideoEventPayload.builder()
                .videoId(video.getVideoId())
                .authorId(video.getAuthorId())
                .title(video.getTitle())
                .summary(video.getSummary())
                .status(video.getStatus())
                .publishTime(video.getPublishTime())
                .updateTime(video.getUpdateTime())
                .build()));
        result.setRepublished(result.getRepublished() + 1);
    }

    /** 全量模式才做：ES 里有、源数据未删列表里没有的文档，重投 video.deleted */
    private void scanOrphans(Set<Long> sourceIds, ReconcileResult result) {
        String after = null;
        while (true) {
            List<Long> ids = esIndexService.scanVideoIds(after, properties.getEsScanSize());
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                if (!sourceIds.contains(id)) {
                    result.setOrphans(result.getOrphans() + 1);
                    eventPublisher.send(new VideoDeletedEvent(
                            VideoDeletedPayload.builder().videoId(id).build()));
                    result.setRepublished(result.getRepublished() + 1);
                }
            }
            after = String.valueOf(ids.getLast());
            if (ids.size() < properties.getEsScanSize()) {
                break;
            }
        }
    }
}
