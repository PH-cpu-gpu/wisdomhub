package com.wisdomhub.content.service.cleanup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.common.mq.event.ArticleDeletedEvent;
import com.wisdomhub.common.mq.event.QuestionDeletedEvent;
import com.wisdomhub.common.mq.event.payload.ArticleDeletedPayload;
import com.wisdomhub.common.mq.event.payload.QuestionDeletedPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.content.config.CleanupProperties;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.constants.ArticleRedisKeyConstants;
import com.wisdomhub.content.constants.ColumnConstants;
import com.wisdomhub.content.constants.CommentConstants;
import com.wisdomhub.content.constants.QuestionConstants;
import com.wisdomhub.content.domain.document.ArticleContent;
import com.wisdomhub.content.domain.entity.Answer;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.domain.entity.Column;
import com.wisdomhub.content.domain.entity.Comment;
import com.wisdomhub.content.domain.entity.Question;
import com.wisdomhub.content.mapper.AnswerMapper;
import com.wisdomhub.content.mapper.ArticleMapper;
import com.wisdomhub.content.mapper.ColumnMapper;
import com.wisdomhub.content.mapper.CommentMapper;
import com.wisdomhub.content.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 按保留期分批物理删墓碑；article/question 删行前重发删除事件。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeletedDataCleanupService {

    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final ColumnMapper columnMapper;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final EventPublisher eventPublisher;
    private final CleanupProperties properties;

    public CleanupResult cleanup(String step) {
        validate();
        CleanupResult result = new CleanupResult();
        String target = step == null || step.isBlank() ? "all" : step.trim().toLowerCase(Locale.ROOT);
        switch (target) {
            case "all" -> {
                cleanupComments(result);
                cleanupAnswers(result);
                cleanupQuestions(result);
                cleanupArticles(result);
                cleanupColumns(result);
            }
            case "comment" -> cleanupComments(result);
            case "answer" -> cleanupAnswers(result);
            case "question" -> cleanupQuestions(result);
            case "article" -> cleanupArticles(result);
            case "column" -> cleanupColumns(result);
            default -> throw new IllegalArgumentException("未知清理步骤: " + step);
        }
        return result;
    }

    private void cleanupComments(CleanupResult result) {
        LocalDateTime cutoff = cutoff();
        int scanned = 0;
        int deleted = 0;
        int failed = 0;
        for (int batch = 0; batch < properties.getMaxBatchesPerRun(); batch++) {
            List<Comment> rows = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                    .select(Comment::getId)
                    .eq(Comment::getDeleted, CommentConstants.DELETED.byteValue())
                    .lt(Comment::getUpdateTime, cutoff)
                    .orderByAsc(Comment::getUpdateTime)
                    .orderByAsc(Comment::getId)
                    .last("LIMIT " + properties.getBatchSize()));
            if (rows.isEmpty()) {
                break;
            }
            scanned += rows.size();
            List<Long> ids = rows.stream().map(Comment::getId).toList();
            try {
                deleted += commentMapper.delete(new LambdaQueryWrapper<Comment>()
                        .in(Comment::getId, ids)
                        .eq(Comment::getDeleted, CommentConstants.DELETED.byteValue()));
            } catch (RuntimeException e) {
                log.error("清理 comment 墓碑失败, size={}", ids.size(), e);
                failed++;
                break;
            }
            if (rows.size() < properties.getBatchSize() || !pause()) {
                break;
            }
        }
        result.add("comment", scanned, deleted, 0, failed);
    }

    private void cleanupAnswers(CleanupResult result) {
        LocalDateTime cutoff = cutoff();
        int scanned = 0;
        int deleted = 0;
        int failed = 0;
        for (int batch = 0; batch < properties.getMaxBatchesPerRun(); batch++) {
            List<Answer> rows = answerMapper.selectList(new LambdaQueryWrapper<Answer>()
                    .select(Answer::getId)
                    .eq(Answer::getDeleted, QuestionConstants.DELETED.byteValue())
                    .lt(Answer::getUpdateTime, cutoff)
                    .orderByAsc(Answer::getUpdateTime)
                    .orderByAsc(Answer::getId)
                    .last("LIMIT " + properties.getBatchSize()));
            if (rows.isEmpty()) {
                break;
            }
            scanned += rows.size();
            List<Long> ids = rows.stream().map(Answer::getId).toList();
            try {
                deleted += answerMapper.delete(new LambdaQueryWrapper<Answer>()
                        .in(Answer::getId, ids)
                        .eq(Answer::getDeleted, QuestionConstants.DELETED.byteValue()));
            } catch (RuntimeException e) {
                log.error("清理 answer 墓碑失败, size={}", ids.size(), e);
                failed++;
                break;
            }
            if (rows.size() < properties.getBatchSize() || !pause()) {
                break;
            }
        }
        result.add("answer", scanned, deleted, 0, failed);
    }

    private void cleanupQuestions(CleanupResult result) {
        LocalDateTime cutoff = cutoff();
        int scanned = 0;
        int deleted = 0;
        int skipped = 0;
        int failed = 0;
        for (int batch = 0; batch < properties.getMaxBatchesPerRun(); batch++) {
            List<Question> rows = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                    .select(Question::getId, Question::getUserId)
                    .eq(Question::getDeleted, QuestionConstants.DELETED.byteValue())
                    .lt(Question::getUpdateTime, cutoff)
                    .orderByAsc(Question::getUpdateTime)
                    .orderByAsc(Question::getId)
                    .last("LIMIT " + properties.getBatchSize()));
            if (rows.isEmpty()) {
                break;
            }
            scanned += rows.size();
            boolean batchSkipped = false;
            List<Long> deletable = new ArrayList<>(rows.size());
            for (Question row : rows) {
                if (properties.isPublishDeleteEvents() && !sendQuestionDeleted(row)) {
                    skipped++;
                    failed++;
                    batchSkipped = true;
                    continue;
                }
                deletable.add(row.getId());
            }
            if (!deletable.isEmpty()) {
                try {
                    deleted += questionMapper.delete(new LambdaQueryWrapper<Question>()
                            .in(Question::getId, deletable)
                            .eq(Question::getDeleted, QuestionConstants.DELETED.byteValue()));
                } catch (RuntimeException e) {
                    log.error("清理 question 墓碑失败, size={}", deletable.size(), e);
                    failed++;
                    break;
                }
            }
            if (batchSkipped || rows.size() < properties.getBatchSize() || !pause()) {
                break;
            }
        }
        result.add("question", scanned, deleted, skipped, failed);
    }

    private void cleanupArticles(CleanupResult result) {
        LocalDateTime cutoff = cutoff();
        int scanned = 0;
        int deleted = 0;
        int skipped = 0;
        int failed = 0;
        for (int batch = 0; batch < properties.getMaxBatchesPerRun(); batch++) {
            List<Article> rows = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                    .select(Article::getId, Article::getAuthorId)
                    .eq(Article::getDeleted, ArticleConstants.DELETED.byteValue())
                    .lt(Article::getUpdateTime, cutoff)
                    .orderByAsc(Article::getUpdateTime)
                    .orderByAsc(Article::getId)
                    .last("LIMIT " + properties.getBatchSize()));
            if (rows.isEmpty()) {
                break;
            }
            scanned += rows.size();
            boolean batchSkipped = false;
            List<Long> deletable = new ArrayList<>(rows.size());
            for (Article row : rows) {
                if (!deleteArticleContent(row.getId())) {
                    skipped++;
                    failed++;
                    batchSkipped = true;
                    continue;
                }
                deleteViewCountKey(row.getId());
                if (properties.isPublishDeleteEvents() && !sendArticleDeleted(row)) {
                    skipped++;
                    failed++;
                    batchSkipped = true;
                    continue;
                }
                deletable.add(row.getId());
            }
            if (!deletable.isEmpty()) {
                try {
                    deleted += articleMapper.delete(new LambdaQueryWrapper<Article>()
                            .in(Article::getId, deletable)
                            .eq(Article::getDeleted, ArticleConstants.DELETED.byteValue()));
                } catch (RuntimeException e) {
                    log.error("清理 article 墓碑失败, size={}", deletable.size(), e);
                    failed++;
                    break;
                }
            }
            if (batchSkipped || rows.size() < properties.getBatchSize() || !pause()) {
                break;
            }
        }
        result.add("article", scanned, deleted, skipped, failed);
    }

    private void cleanupColumns(CleanupResult result) {
        LocalDateTime cutoff = cutoff();
        Long protectedCount = columnMapper.selectCount(new LambdaQueryWrapper<Column>()
                .eq(Column::getDeleted, ColumnConstants.DELETED.byteValue())
                .lt(Column::getUpdateTime, cutoff)
                .inSql(Column::getId, "SELECT column_id FROM column_purchase"));
        int skipped = protectedCount == null ? 0 : protectedCount.intValue();
        if (skipped > 0) {
            log.warn("存在购买记录的专栏墓碑不清理, count={}", skipped);
        }

        int scanned = 0;
        int deleted = 0;
        int failed = 0;
        for (int batch = 0; batch < properties.getMaxBatchesPerRun(); batch++) {
            List<Column> rows = columnMapper.selectList(new LambdaQueryWrapper<Column>()
                    .select(Column::getId)
                    .eq(Column::getDeleted, ColumnConstants.DELETED.byteValue())
                    .lt(Column::getUpdateTime, cutoff)
                    .notInSql(Column::getId, "SELECT column_id FROM column_purchase")
                    .orderByAsc(Column::getUpdateTime)
                    .orderByAsc(Column::getId)
                    .last("LIMIT " + properties.getBatchSize()));
            if (rows.isEmpty()) {
                break;
            }
            scanned += rows.size();
            List<Long> ids = rows.stream().map(Column::getId).toList();
            try {
                deleted += columnMapper.delete(new LambdaQueryWrapper<Column>()
                        .in(Column::getId, ids)
                        .eq(Column::getDeleted, ColumnConstants.DELETED.byteValue()));
            } catch (RuntimeException e) {
                log.error("清理 column 墓碑失败, size={}", ids.size(), e);
                failed++;
                break;
            }
            if (rows.size() < properties.getBatchSize() || !pause()) {
                break;
            }
        }
        result.add("column", scanned, deleted, skipped, failed);
    }

    private boolean sendQuestionDeleted(Question row) {
        try {
            eventPublisher.send(new QuestionDeletedEvent(QuestionDeletedPayload.builder()
                    .questionId(row.getId())
                    .userId(row.getUserId())
                    .build()));
            return true;
        } catch (RuntimeException e) {
            log.error("重发 question.deleted 失败, questionId={}", row.getId(), e);
            return false;
        }
    }

    private boolean sendArticleDeleted(Article row) {
        try {
            eventPublisher.send(new ArticleDeletedEvent(ArticleDeletedPayload.builder()
                    .articleId(row.getId())
                    .authorId(row.getAuthorId())
                    .build()));
            return true;
        } catch (RuntimeException e) {
            log.error("重发 article.deleted 失败, articleId={}", row.getId(), e);
            return false;
        }
    }

    private boolean deleteArticleContent(Long articleId) {
        try {
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(articleId)), ArticleContent.class);
            return true;
        } catch (RuntimeException e) {
            log.error("清理文章正文失败, articleId={}", articleId, e);
            return false;
        }
    }

    private void deleteViewCountKey(Long articleId) {
        try {
            stringRedisTemplate.delete(ArticleRedisKeyConstants.VIEW_COUNT_KEY + articleId);
        } catch (RuntimeException e) {
            // 阅读量 key 是缓存，删失败由 articleViewCountSyncHandler 的孤儿清理兜底
            log.warn("清理阅读量 key 失败, articleId={}", articleId, e);
        }
    }

    private LocalDateTime cutoff() {
        return LocalDateTime.now(ZoneOffset.UTC).minusDays(properties.getRetentionDays());
    }

    private boolean pause() {
        if (properties.getPauseMillis() <= 0) {
            return true;
        }
        try {
            Thread.sleep(properties.getPauseMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void validate() {
        if (properties.getRetentionDays() <= 0) {
            throw new IllegalArgumentException("wisdomhub.cleanup.retention-days 必须大于 0");
        }
        if (properties.getBatchSize() <= 0) {
            throw new IllegalArgumentException("wisdomhub.cleanup.batch-size 必须大于 0");
        }
        if (properties.getMaxBatchesPerRun() <= 0) {
            throw new IllegalArgumentException("wisdomhub.cleanup.max-batches-per-run 必须大于 0");
        }
    }
}
