package com.wisdomhub.content.service.reconcile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.content.config.CountReconcileProperties;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.constants.ColumnConstants;
import com.wisdomhub.content.constants.CommentConstants;
import com.wisdomhub.content.constants.QuestionConstants;
import com.wisdomhub.content.domain.entity.Answer;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.domain.entity.Column;
import com.wisdomhub.content.domain.entity.Question;
import com.wisdomhub.content.mapper.AnswerMapper;
import com.wisdomhub.content.mapper.ArticleMapper;
import com.wisdomhub.content.mapper.ColumnMapper;
import com.wisdomhub.content.mapper.CountReconcileMapper;
import com.wisdomhub.content.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 以明细 COUNT(*) 为准校正快照列；只写不等的行，且不 bump update_time。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountReconcileService {

    private final CountReconcileMapper countReconcileMapper;
    private final ArticleMapper articleMapper;
    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final ColumnMapper columnMapper;
    private final CountReconcileProperties properties;

    public CountReconcileResult reconcile() {
        validate();
        CountReconcileResult result = new CountReconcileResult();
        reconcileArticles(result);
        reconcileQuestions(result);
        reconcileAnswers(result);
        reconcileColumns(result);
        return result;
    }

    private void reconcileArticles(CountReconcileResult result) {
        Map<Long, Long> likes = toCountMap(
                countReconcileMapper.countArticleLikesByArticle(), "articleId");
        Map<Long, Long> favorites = toCountMap(
                countReconcileMapper.countArticleFavoritesByArticle(), "articleId");
        Map<Long, Long> comments = toCountMap(
                countReconcileMapper.countCommentsByTarget(CommentConstants.TARGET_TYPE_ARTICLE), "targetId");

        int scanned = 0;
        int fixedRows = 0;
        int fixedColumns = 0;
        int failed = 0;
        long lastId = 0;
        while (true) {
            List<Article> batch;
            try {
                batch = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .select(Article::getId, Article::getLikeCount,
                                Article::getFavoriteCount, Article::getCommentCount)
                        .eq(Article::getDeleted, ArticleConstants.NOT_DELETED.byteValue())
                        .gt(Article::getId, lastId)
                        .orderByAsc(Article::getId)
                        .last("LIMIT " + properties.getBatchSize()));
            } catch (RuntimeException e) {
                log.error("扫描 article 失败, lastId={}", lastId, e);
                failed++;
                break;
            }
            if (batch.isEmpty()) {
                break;
            }
            scanned += batch.size();
            try {
                for (Article row : batch) {
                    int actualLike = toInt(likes.getOrDefault(row.getId(), 0L));
                    int actualFavorite = toInt(favorites.getOrDefault(row.getId(), 0L));
                    int actualComment = toInt(comments.getOrDefault(row.getId(), 0L));
                    boolean likeChanged = !Objects.equals(row.getLikeCount(), actualLike);
                    boolean favoriteChanged = !Objects.equals(row.getFavoriteCount(), actualFavorite);
                    boolean commentChanged = !Objects.equals(row.getCommentCount(), actualComment);
                    if (!likeChanged && !favoriteChanged && !commentChanged) {
                        continue;
                    }
                    LambdaUpdateWrapper<Article> update = new LambdaUpdateWrapper<Article>()
                            .eq(Article::getId, row.getId())
                            .eq(Article::getDeleted, ArticleConstants.NOT_DELETED.byteValue());
                    int changedColumns = 0;
                    if (likeChanged) {
                        update.set(Article::getLikeCount, actualLike);
                        changedColumns++;
                    }
                    if (favoriteChanged) {
                        update.set(Article::getFavoriteCount, actualFavorite);
                        changedColumns++;
                    }
                    if (commentChanged) {
                        update.set(Article::getCommentCount, actualComment);
                        changedColumns++;
                    }
                    if (articleMapper.update(null, update) > 0) {
                        fixedRows++;
                        fixedColumns += changedColumns;
                    }
                }
            } catch (RuntimeException e) {
                log.error("校正 article 失败, lastId={}", lastId, e);
                failed++;
                break;
            }
            lastId = batch.get(batch.size() - 1).getId();
            if (batch.size() < properties.getBatchSize() || !pause()) {
                break;
            }
        }
        result.add("article", scanned, fixedRows, fixedColumns, failed);
    }

    private void reconcileQuestions(CountReconcileResult result) {
        Map<Long, Long> answers = toCountMap(
                countReconcileMapper.countAnswersByQuestion(), "questionId");
        Map<Long, Long> favorites = toCountMap(
                countReconcileMapper.countQuestionFavoritesByQuestion(), "questionId");

        int scanned = 0;
        int fixedRows = 0;
        int fixedColumns = 0;
        int failed = 0;
        long lastId = 0;
        while (true) {
            List<Question> batch;
            try {
                batch = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .select(Question::getId, Question::getAnswerCount, Question::getFavoriteCount)
                        .eq(Question::getDeleted, QuestionConstants.NOT_DELETED.byteValue())
                        .gt(Question::getId, lastId)
                        .orderByAsc(Question::getId)
                        .last("LIMIT " + properties.getBatchSize()));
            } catch (RuntimeException e) {
                log.error("扫描 question 失败, lastId={}", lastId, e);
                failed++;
                break;
            }
            if (batch.isEmpty()) {
                break;
            }
            scanned += batch.size();
            try {
                for (Question row : batch) {
                    int actualAnswer = toInt(answers.getOrDefault(row.getId(), 0L));
                    int actualFavorite = toInt(favorites.getOrDefault(row.getId(), 0L));
                    boolean answerChanged = !Objects.equals(row.getAnswerCount(), actualAnswer);
                    boolean favoriteChanged = !Objects.equals(row.getFavoriteCount(), actualFavorite);
                    if (!answerChanged && !favoriteChanged) {
                        continue;
                    }
                    LambdaUpdateWrapper<Question> update = new LambdaUpdateWrapper<Question>()
                            .eq(Question::getId, row.getId())
                            .eq(Question::getDeleted, QuestionConstants.NOT_DELETED.byteValue());
                    int changedColumns = 0;
                    if (answerChanged) {
                        update.set(Question::getAnswerCount, actualAnswer);
                        changedColumns++;
                    }
                    if (favoriteChanged) {
                        update.set(Question::getFavoriteCount, actualFavorite);
                        changedColumns++;
                    }
                    if (questionMapper.update(null, update) > 0) {
                        fixedRows++;
                        fixedColumns += changedColumns;
                    }
                }
            } catch (RuntimeException e) {
                log.error("校正 question 失败, lastId={}", lastId, e);
                failed++;
                break;
            }
            lastId = batch.get(batch.size() - 1).getId();
            if (batch.size() < properties.getBatchSize() || !pause()) {
                break;
            }
        }
        result.add("question", scanned, fixedRows, fixedColumns, failed);
    }

    private void reconcileAnswers(CountReconcileResult result) {
        Map<Long, Long> likes = toCountMap(
                countReconcileMapper.countAnswerLikesByAnswer(), "answerId");
        Map<Long, Long> comments = toCountMap(
                countReconcileMapper.countCommentsByTarget(CommentConstants.TARGET_TYPE_ANSWER), "targetId");

        int scanned = 0;
        int fixedRows = 0;
        int fixedColumns = 0;
        int failed = 0;
        long lastId = 0;
        while (true) {
            List<Answer> batch;
            try {
                batch = answerMapper.selectList(new LambdaQueryWrapper<Answer>()
                        .select(Answer::getId, Answer::getLikeCount, Answer::getCommentCount)
                        .eq(Answer::getDeleted, QuestionConstants.NOT_DELETED.byteValue())
                        .gt(Answer::getId, lastId)
                        .orderByAsc(Answer::getId)
                        .last("LIMIT " + properties.getBatchSize()));
            } catch (RuntimeException e) {
                log.error("扫描 answer 失败, lastId={}", lastId, e);
                failed++;
                break;
            }
            if (batch.isEmpty()) {
                break;
            }
            scanned += batch.size();
            try {
                for (Answer row : batch) {
                    int actualLike = toInt(likes.getOrDefault(row.getId(), 0L));
                    int actualComment = toInt(comments.getOrDefault(row.getId(), 0L));
                    boolean likeChanged = !Objects.equals(row.getLikeCount(), actualLike);
                    boolean commentChanged = !Objects.equals(row.getCommentCount(), actualComment);
                    if (!likeChanged && !commentChanged) {
                        continue;
                    }
                    LambdaUpdateWrapper<Answer> update = new LambdaUpdateWrapper<Answer>()
                            .eq(Answer::getId, row.getId())
                            .eq(Answer::getDeleted, QuestionConstants.NOT_DELETED.byteValue());
                    int changedColumns = 0;
                    if (likeChanged) {
                        update.set(Answer::getLikeCount, actualLike);
                        changedColumns++;
                    }
                    if (commentChanged) {
                        update.set(Answer::getCommentCount, actualComment);
                        changedColumns++;
                    }
                    if (answerMapper.update(null, update) > 0) {
                        fixedRows++;
                        fixedColumns += changedColumns;
                    }
                }
            } catch (RuntimeException e) {
                log.error("校正 answer 失败, lastId={}", lastId, e);
                failed++;
                break;
            }
            lastId = batch.get(batch.size() - 1).getId();
            if (batch.size() < properties.getBatchSize() || !pause()) {
                break;
            }
        }
        result.add("answer", scanned, fixedRows, fixedColumns, failed);
    }

    private void reconcileColumns(CountReconcileResult result) {
        Map<Long, Long> articles = toCountMap(
                countReconcileMapper.countColumnArticlesByColumn(), "columnId");

        int scanned = 0;
        int fixedRows = 0;
        int fixedColumns = 0;
        int failed = 0;
        long lastId = 0;
        while (true) {
            List<Column> batch;
            try {
                batch = columnMapper.selectList(new LambdaQueryWrapper<Column>()
                        .select(Column::getId, Column::getArticleCount)
                        .eq(Column::getDeleted, ColumnConstants.NOT_DELETED.byteValue())
                        .gt(Column::getId, lastId)
                        .orderByAsc(Column::getId)
                        .last("LIMIT " + properties.getBatchSize()));
            } catch (RuntimeException e) {
                log.error("扫描 column 失败, lastId={}", lastId, e);
                failed++;
                break;
            }
            if (batch.isEmpty()) {
                break;
            }
            scanned += batch.size();
            try {
                for (Column row : batch) {
                    int actual = toInt(articles.getOrDefault(row.getId(), 0L));
                    if (Objects.equals(row.getArticleCount(), actual)) {
                        continue;
                    }
                    LambdaUpdateWrapper<Column> update = new LambdaUpdateWrapper<Column>()
                            .set(Column::getArticleCount, actual)
                            .eq(Column::getId, row.getId())
                            .eq(Column::getDeleted, ColumnConstants.NOT_DELETED.byteValue());
                    if (columnMapper.update(null, update) > 0) {
                        fixedRows++;
                        fixedColumns++;
                    }
                }
            } catch (RuntimeException e) {
                log.error("校正 column 失败, lastId={}", lastId, e);
                failed++;
                break;
            }
            lastId = batch.get(batch.size() - 1).getId();
            if (batch.size() < properties.getBatchSize() || !pause()) {
                break;
            }
        }
        result.add("column", scanned, fixedRows, fixedColumns, failed);
    }

    private static Map<Long, Long> toCountMap(List<Map<String, Object>> rows, String keyName) {
        Map<Long, Long> counts = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object id = row.get(keyName);
            Object cnt = row.get("cnt");
            if (id instanceof Number idNumber && cnt instanceof Number countNumber) {
                counts.put(idNumber.longValue(), countNumber.longValue());
            }
        }
        if (counts.isEmpty() && !rows.isEmpty()) {
            // 防止 alias 大小写不一致时把全表计数误校正成 0
            throw new IllegalStateException("计数查询返回了行但无法解析 key=" + keyName);
        }
        return counts;
    }

    private static int toInt(long value) {
        if (value > Integer.MAX_VALUE) {
            log.warn("计数超过 INT 上限, 按 Integer.MAX_VALUE 落库: {}", value);
            return Integer.MAX_VALUE;
        }
        return (int) value;
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
        if (properties.getBatchSize() <= 0) {
            throw new IllegalArgumentException("wisdomhub.reconcile.count.batch-size 必须大于 0");
        }
    }
}
