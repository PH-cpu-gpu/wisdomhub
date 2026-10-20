package com.wisdomhub.search.service.reconcile;

import com.wisdomhub.common.mq.event.QuestionCreatedEvent;
import com.wisdomhub.common.mq.event.QuestionDeletedEvent;
import com.wisdomhub.common.mq.event.payload.QuestionDeletedPayload;
import com.wisdomhub.common.mq.event.payload.QuestionEventPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.search.api.ContentFeign;
import com.wisdomhub.search.api.dto.QuestionSyncDTO;
import com.wisdomhub.search.config.ReconcileProperties;
import com.wisdomhub.search.domain.document.QuestionIndexDoc;
import com.wisdomhub.search.service.EsIndexService;
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
 * question_index 对账。增量窗口看 update_time；计数与采纳状态变化不一定会 bump update_time，
 * 所以每日全量还要再比一次计数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionReconcileService {

    private static final int DELETED = 1;

    private static final String EPOCH = "1970-01-01T00:00:00";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ContentFeign contentFeign;
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
            List<QuestionSyncDTO> page = contentFeign.syncQuestions(since, lastId, properties.getPageSize());
            if (page.isEmpty()) {
                break;
            }
            result.setScanned(result.getScanned() + page.size());
            List<Long> ids = page.stream().map(QuestionSyncDTO::getQuestionId).filter(Objects::nonNull).toList();
            Map<Long, QuestionIndexDoc> docs = esIndexService.mgetQuestions(ids);
            for (QuestionSyncDTO question : page) {
                if (question.getQuestionId() == null) {
                    continue;
                }
                sourceIds.add(question.getQuestionId());
                compareAndPublish(question, docs.get(question.getQuestionId()), result);
            }
            QuestionSyncDTO last = page.getLast();
            if (last.getUpdateTime() == null) {
                log.warn("问题 update_time 为空，停止本页推进: questionId={}", last.getQuestionId());
                break;
            }
            since = last.getUpdateTime().format(ISO);
            lastId = last.getQuestionId();
            if (page.size() < properties.getPageSize()) {
                break;
            }
        }

        if (full) {
            scanOrphans(sourceIds, result);
        }
        return result;
    }

    private void compareAndPublish(QuestionSyncDTO question, QuestionIndexDoc doc, ReconcileResult result) {
        if (question.getDeleted() != null && question.getDeleted() == DELETED) {
            if (doc != null) {
                result.setMismatched(result.getMismatched() + 1);
                eventPublisher.send(new QuestionDeletedEvent(QuestionDeletedPayload.builder()
                        .questionId(question.getQuestionId())
                        .userId(question.getUserId())
                        .build()));
                result.setRepublished(result.getRepublished() + 1);
            }
            return;
        }
        if (doc == null || !sameQuestion(question, doc)) {
            result.setMismatched(result.getMismatched() + 1);
            eventPublisher.send(new QuestionCreatedEvent(toPayload(question)));
            result.setRepublished(result.getRepublished() + 1);
        }
    }

    private boolean sameQuestion(QuestionSyncDTO source, QuestionIndexDoc doc) {
        return Objects.equals(source.getTitle(), doc.getTitle())
                && Objects.equals(source.getContent(), doc.getContent())
                && Objects.equals(source.getUserId(), doc.getUserId())
                && Objects.equals(source.getAnswerCount(), doc.getAnswerCount())
                && Objects.equals(source.getFavoriteCount(), doc.getFavoriteCount())
                && Objects.equals(source.getAcceptedAnswerId(), doc.getAcceptedAnswerId())
                && sameTime(source.getUpdateTime(), doc.getUpdateTime());
    }

    private boolean sameTime(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.truncatedTo(ChronoUnit.SECONDS).equals(right.truncatedTo(ChronoUnit.SECONDS));
    }

    private QuestionEventPayload toPayload(QuestionSyncDTO question) {
        return QuestionEventPayload.builder()
                .questionId(question.getQuestionId())
                .userId(question.getUserId())
                .title(question.getTitle())
                .content(question.getContent())
                .answerCount(question.getAnswerCount())
                .favoriteCount(question.getFavoriteCount())
                .acceptedAnswerId(question.getAcceptedAnswerId())
                .createTime(question.getCreateTime())
                .updateTime(question.getUpdateTime())
                .build();
    }

    private void scanOrphans(Set<Long> sourceIds, ReconcileResult result) {
        String after = null;
        while (true) {
            List<Long> ids = esIndexService.scanQuestionIds(after, properties.getEsScanSize());
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                if (!sourceIds.contains(id)) {
                    result.setOrphans(result.getOrphans() + 1);
                    eventPublisher.send(new QuestionDeletedEvent(
                            QuestionDeletedPayload.builder().questionId(id).build()));
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
