package com.wisdomhub.search.service.reconcile;

import com.wisdomhub.common.mq.event.TagCreatedEvent;
import com.wisdomhub.common.mq.event.TagDeletedEvent;
import com.wisdomhub.common.mq.event.TagUpdatedEvent;
import com.wisdomhub.common.mq.event.payload.TagDeletedPayload;
import com.wisdomhub.common.mq.event.payload.TagEventPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.search.api.ContentFeign;
import com.wisdomhub.search.api.dto.TagSyncDTO;
import com.wisdomhub.search.config.ReconcileProperties;
import com.wisdomhub.search.domain.document.TagIndexDoc;
import com.wisdomhub.search.service.EsIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * tag_index 对账。标签表很小，每次都全量扫；tag.updated 还会顺带修 article_index 里的标签名。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagReconcileService {

    private static final String EPOCH = "1970-01-01T00:00:00";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ContentFeign contentFeign;
    private final EsIndexService esIndexService;
    private final EventPublisher eventPublisher;
    private final ReconcileProperties properties;

    public ReconcileResult reconcile() {
        ReconcileResult result = new ReconcileResult();
        Set<Long> sourceIds = new HashSet<>();
        String since = EPOCH;
        long lastId = 0L;

        while (true) {
            List<TagSyncDTO> page = contentFeign.syncTags(since, lastId, properties.getPageSize());
            if (page.isEmpty()) {
                break;
            }
            result.setScanned(result.getScanned() + page.size());
            List<Long> ids = page.stream().map(TagSyncDTO::getTagId).filter(Objects::nonNull).toList();
            Map<Long, TagIndexDoc> docs = esIndexService.mgetTags(ids);
            for (TagSyncDTO tag : page) {
                if (tag.getTagId() == null) {
                    continue;
                }
                sourceIds.add(tag.getTagId());
                compareAndPublish(tag, docs.get(tag.getTagId()), result);
            }
            TagSyncDTO last = page.getLast();
            if (last.getUpdateTime() == null) {
                log.warn("标签 update_time 为空，停止本页推进: tagId={}", last.getTagId());
                break;
            }
            since = last.getUpdateTime().format(ISO);
            lastId = last.getTagId();
            if (page.size() < properties.getPageSize()) {
                break;
            }
        }

        scanOrphans(sourceIds, result);
        return result;
    }

    private void compareAndPublish(TagSyncDTO tag, TagIndexDoc doc, ReconcileResult result) {
        if (doc == null) {
            result.setMismatched(result.getMismatched() + 1);
            eventPublisher.send(new TagCreatedEvent(toPayload(tag)));
            result.setRepublished(result.getRepublished() + 1);
            return;
        }
        if (!sameTag(tag, doc)) {
            result.setMismatched(result.getMismatched() + 1);
            eventPublisher.send(new TagUpdatedEvent(toPayload(tag)));
            result.setRepublished(result.getRepublished() + 1);
        }
    }

    private boolean sameTag(TagSyncDTO source, TagIndexDoc doc) {
        return Objects.equals(source.getName(), doc.getName())
                && Objects.equals(source.getCategory(), doc.getCategory())
                && Objects.equals(source.getDescription(), doc.getDescription())
                && sameTime(source.getUpdateTime(), doc.getUpdateTime());
    }

    private boolean sameTime(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.truncatedTo(ChronoUnit.SECONDS).equals(right.truncatedTo(ChronoUnit.SECONDS));
    }

    private TagEventPayload toPayload(TagSyncDTO tag) {
        return TagEventPayload.builder()
                .tagId(tag.getTagId())
                .name(tag.getName())
                .category(tag.getCategory())
                .description(tag.getDescription())
                .createTime(tag.getCreateTime())
                .updateTime(tag.getUpdateTime())
                .build();
    }

    private void scanOrphans(Set<Long> sourceIds, ReconcileResult result) {
        String after = null;
        while (true) {
            List<Long> ids = esIndexService.scanTagIds(after, properties.getEsScanSize());
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                if (!sourceIds.contains(id)) {
                    result.setOrphans(result.getOrphans() + 1);
                    eventPublisher.send(new TagDeletedEvent(TagDeletedPayload.builder().tagId(id).build()));
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
