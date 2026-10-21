package com.wisdomhub.content.service.cleanup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Projections;
import com.mongodb.client.result.DeleteResult;
import com.wisdomhub.content.config.CleanupProperties;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.domain.document.ArticleContent;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** article_content 里 _id 不在未删 article 中的文档 = 正文孤儿。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MongoContentCleanupService {

    private static final String COLLECTION = "article_content";

    private final MongoTemplate mongoTemplate;
    private final ArticleMapper articleMapper;
    private final CleanupProperties properties;

    public CleanupResult cleanup() {
        if (properties.getMongoBatchSize() <= 0) {
            throw new IllegalArgumentException("wisdomhub.cleanup.mongo-batch-size 必须大于 0");
        }
        CleanupResult result = new CleanupResult();
        int scanned = 0;
        int deleted = 0;
        int failed = 0;
        List<Long> batch = new ArrayList<>(properties.getMongoBatchSize());
        try (MongoCursor<Document> cursor = mongoTemplate.getCollection(COLLECTION)
                .find()
                .projection(Projections.include("_id"))
                .batchSize(properties.getMongoBatchSize())
                .iterator()) {
            while (cursor.hasNext()) {
                Object id = cursor.next().get("_id");
                if (!(id instanceof Number number)) {
                    continue;
                }
                batch.add(number.longValue());
                if (batch.size() >= properties.getMongoBatchSize()) {
                    scanned += batch.size();
                    BatchOutcome outcome = cleanBatch(batch);
                    deleted += outcome.deleted();
                    failed += outcome.failed();
                    batch.clear();
                }
            }
        } catch (RuntimeException e) {
            log.error("扫描 article_content 失败", e);
            failed++;
        }
        if (!batch.isEmpty()) {
            scanned += batch.size();
            BatchOutcome outcome = cleanBatch(batch);
            deleted += outcome.deleted();
            failed += outcome.failed();
        }
        result.add(COLLECTION, scanned, deleted, 0, failed);
        return result;
    }

    private BatchOutcome cleanBatch(List<Long> ids) {
        try {
            Set<Long> active = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                            .select(Article::getId)
                            .in(Article::getId, ids)
                            .eq(Article::getDeleted, ArticleConstants.NOT_DELETED.byteValue()))
                    .stream()
                    .map(Article::getId)
                    .collect(Collectors.toSet());
            List<Long> orphans = ids.stream().filter(id -> !active.contains(id)).toList();
            if (orphans.isEmpty()) {
                return new BatchOutcome(0, 0);
            }
            DeleteResult deleteResult = mongoTemplate.remove(
                    Query.query(Criteria.where("_id").in(orphans)), ArticleContent.class);
            return new BatchOutcome((int) deleteResult.getDeletedCount(), 0);
        } catch (RuntimeException e) {
            log.error("清理 article_content 批失败, size={}", ids.size(), e);
            return new BatchOutcome(0, 1);
        }
    }

    private record BatchOutcome(int deleted, int failed) {
    }
}
