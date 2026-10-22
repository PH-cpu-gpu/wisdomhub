package com.wisdomhub.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateByQueryResponse;
import co.elastic.clients.elasticsearch.core.get.GetResult;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.search.domain.document.ArticleIndexDoc;
import com.wisdomhub.search.domain.document.QuestionIndexDoc;
import com.wisdomhub.search.domain.document.TagIndexDoc;
import com.wisdomhub.search.domain.document.UserIndexDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES 的写入出口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsIndexService {

    public static final String ARTICLE_INDEX = "article_index";
    public static final String QUESTION_INDEX = "question_index";
    public static final String USER_INDEX = "user_index";
    public static final String TAG_INDEX = "tag_index";

    /** 标签改名的就地更新。 */
    private static final String RENAME_TAG_IN_ARTICLES = """
            if (ctx._source.tags != null) {
              for (def t : ctx._source.tags) {
                if (params.tagId.equals(t.tagId)) {
                  t.name = params.name;
                  t.category = params.category;
                }
              }
            }
            """;

    private static final String REMOVE_TAG_FROM_ARTICLES = """
            if (ctx._source.tags != null) {
              for (int i = ctx._source.tags.size() - 1; i >= 0; i--) {
                if (params.tagId.equals(ctx._source.tags[i].tagId)) {
                  ctx._source.tags.remove(i);
                }
              }
            }
            """;

    private final ElasticsearchClient elasticsearchClient;

    public void indexArticle(ArticleIndexDoc doc) {
        index(ARTICLE_INDEX, "文章", doc.getArticleId(), doc);
    }

    public void indexQuestion(QuestionIndexDoc doc) {
        index(QUESTION_INDEX, "问题", doc.getQuestionId(), doc);
    }

    public void indexUser(UserIndexDoc doc) {
        index(USER_INDEX, "用户", doc.getUserId(), doc);
    }

    public void indexTag(TagIndexDoc doc) {
        index(TAG_INDEX, "标签", doc.getTagId(), doc);
    }

    public void deleteArticle(Long articleId) {
        delete(ARTICLE_INDEX, "文章", articleId);
    }

    public void deleteQuestion(Long questionId) {
        delete(QUESTION_INDEX, "问题", questionId);
    }

    public void deleteTag(Long tagId) {
        delete(TAG_INDEX, "标签", tagId);
    }

    public void deleteUser(Long userId) {
        delete(USER_INDEX, "用户", userId);
    }

    /**
     * 标签改名 / 改分组后修 article_index 里的残留。
     */
    public void renameTagInArticles(Long tagId, String name, String category) {
        updateTagInArticles("标签改名", RENAME_TAG_IN_ARTICLES, tagId, Map.of(
                "tagId", JsonData.of(String.valueOf(tagId)),
                "name", JsonData.of(name),
                "category", JsonData.of(category)));
    }

    /** 标签被物理删掉后，把它从所有文章的 tags 数组里摘下来 */
    public void removeTagFromArticles(Long tagId) {
        updateTagInArticles("标签删除", REMOVE_TAG_FROM_ARTICLES, tagId,
                Map.of("tagId", JsonData.of(String.valueOf(tagId))));
    }

    // ==================== 对账读取（B2.29）====================

    public ArticleIndexDoc getArticle(Long articleId) {
        return get(ARTICLE_INDEX, articleId, ArticleIndexDoc.class);
    }

    public QuestionIndexDoc getQuestion(Long questionId) {
        return get(QUESTION_INDEX, questionId, QuestionIndexDoc.class);
    }

    public TagIndexDoc getTag(Long tagId) {
        return get(TAG_INDEX, tagId, TagIndexDoc.class);
    }

    public Map<Long, ArticleIndexDoc> mgetArticles(List<Long> articleIds) {
        return mget(ARTICLE_INDEX, articleIds, ArticleIndexDoc.class);
    }

    public Map<Long, QuestionIndexDoc> mgetQuestions(List<Long> questionIds) {
        return mget(QUESTION_INDEX, questionIds, QuestionIndexDoc.class);
    }

    public Map<Long, TagIndexDoc> mgetTags(List<Long> tagIds) {
        return mget(TAG_INDEX, tagIds, TagIndexDoc.class);
    }

    public Map<Long, UserIndexDoc> mgetUsers(List<Long> userIds) {
        return mget(USER_INDEX, userIds, UserIndexDoc.class);
    }

    /** 按 ID 字段升序扫一页文档 ID，供全量孤儿检测；afterId 为上一页最后一个 ID */
    public List<Long> scanArticleIds(String afterId, int size) {
        return scanIds(ARTICLE_INDEX, "articleId", afterId, size);
    }

    public List<Long> scanQuestionIds(String afterId, int size) {
        return scanIds(QUESTION_INDEX, "questionId", afterId, size);
    }

    public List<Long> scanTagIds(String afterId, int size) {
        return scanIds(TAG_INDEX, "tagId", afterId, size);
    }

    public List<Long> scanUserIds(String afterId, int size) {
        return scanIds(USER_INDEX, "userId", afterId, size);
    }

    private <T> T get(String indexName, Long id, Class<T> type) {
        try {
            GetResponse<T> response = elasticsearchClient.get(g -> g
                    .index(indexName)
                    .id(String.valueOf(id)), type);
            return response.found() ? response.source() : null;
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "读取 ES 文档失败: " + e.getMessage());
        }
    }

    private <T> Map<Long, T> mget(String indexName, List<Long> ids, Class<T> type) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        try {
            MgetResponse<T> response = elasticsearchClient.mget(m -> m
                    .index(indexName)
                    .ids(ids.stream().map(String::valueOf).toList()), type);
            Map<Long, T> result = new HashMap<>(ids.size());
            for (MultiGetResponseItem<T> item : response.docs()) {
                if (item.isResult()) {
                    GetResult<T> doc = item.result();
                    if (doc.found() && doc.source() != null) {
                        result.put(Long.valueOf(doc.id()), doc.source());
                    }
                }
            }
            return result;
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "批量读取 ES 文档失败: " + e.getMessage());
        }
    }

    private List<Long> scanIds(String indexName, String idField, String afterId, int size) {
        try {
            SearchResponse<Object> response = elasticsearchClient.search(s -> {
                s.index(indexName)
                        .size(size)
                        .source(src -> src.fetch(false))
                        .sort(so -> so.field(f -> f.field(idField)));
                if (afterId != null) {
                    s.searchAfter(afterId);
                }
                return s;
            }, Object.class);
            return response.hits().hits().stream()
                    .map(Hit::id)
                    .map(Long::valueOf)
                    .toList();
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "扫描 ES 文档失败: " + e.getMessage());
        }
    }

    private void index(String indexName, String label, Long id, Object doc) {
        try {
            var response = elasticsearchClient.index(i -> i
                    .index(indexName)
                    .id(String.valueOf(id))
                    .document(doc));
            log.info("{}索引已写入, id={}, result={}", label, id, response.result().jsonValue());
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "写入 ES 失败: " + e.getMessage());
        }
    }

    /** 删不存在的文档只返回 not_found、不抛错，所以下架/删除/重复投递都走这一条 */
    private void delete(String indexName, String label, Long id) {
        try {
            var response = elasticsearchClient.delete(d -> d
                    .index(indexName)
                    .id(String.valueOf(id)));
            log.info("{}索引已删除, id={}, result={}", label, id, response.result().jsonValue());
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "删除 ES 文档失败: " + e.getMessage());
        }
    }

    /**
     * 更新 article_index 中关于标签的信息
     */
    private void updateTagInArticles(String label, String source, Long tagId, Map<String, JsonData> params) {
        String id = String.valueOf(tagId);
        try {
            UpdateByQueryResponse response = elasticsearchClient.updateByQuery(u -> u
                    .index(ARTICLE_INDEX)
                    .query(q -> q.term(t -> t.field("tags.tagId").value(id)))
                    .conflicts(Conflicts.Proceed)
                    .script(s -> s.lang(ScriptLanguage.Painless)
                            .source(source)
                            .params(params)));
            if (!response.failures().isEmpty()) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                        label + "回填文章标签失败: " + response.failures().getFirst().cause().reason());
            }
            log.info("{}回填文章标签完成, tagId={}, updated={}", label, tagId, response.updated());
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新 ES 文档失败: " + e.getMessage());
        }
    }
}
