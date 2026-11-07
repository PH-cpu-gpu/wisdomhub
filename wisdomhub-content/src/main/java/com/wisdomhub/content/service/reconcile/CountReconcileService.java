package com.wisdomhub.content.service.reconcile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wisdomhub.content.config.CountReconcileProperties;
import com.wisdomhub.content.constants.ColumnConstants;
import com.wisdomhub.content.constants.QuestionConstants;
import com.wisdomhub.content.domain.entity.Column;
import com.wisdomhub.content.domain.entity.Question;
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

/** 内容自有计数对账：question.answer_count 与 column.article_count。互动计数由 interaction 对账。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountReconcileService {

    private final CountReconcileMapper countReconcileMapper;
    private final QuestionMapper questionMapper;
    private final ColumnMapper columnMapper;
    private final CountReconcileProperties properties;

    public CountReconcileResult reconcile() {
        validate();
        CountReconcileResult result = new CountReconcileResult();
        reconcileQuestions(result);
        reconcileColumns(result);
        return result;
    }

    private void reconcileQuestions(CountReconcileResult result) {
        Map<Long, Long> answers = toCountMap(
                countReconcileMapper.countAnswersByQuestion(), "questionId");

        int scanned = 0;
        int fixedRows = 0;
        int fixedColumns = 0;
        int failed = 0;
        long lastId = 0;
        while (true) {
            List<Question> batch;
            try {
                batch = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .select(Question::getId, Question::getAnswerCount)
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
                    int actual = toInt(answers.getOrDefault(row.getId(), 0L));
                    if (Objects.equals(row.getAnswerCount(), actual)) {
                        continue;
                    }
                    LambdaUpdateWrapper<Question> update = new LambdaUpdateWrapper<Question>()
                            .set(Question::getAnswerCount, actual)
                            .eq(Question::getId, row.getId())
                            .eq(Question::getDeleted, QuestionConstants.NOT_DELETED.byteValue());
                    if (questionMapper.update(null, update) > 0) {
                        fixedRows++;
                        fixedColumns++;
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
