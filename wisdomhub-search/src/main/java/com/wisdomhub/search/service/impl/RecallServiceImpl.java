package com.wisdomhub.search.service.impl;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.search.constants.SearchConstants;
import com.wisdomhub.search.domain.document.ArticleIndexDoc;
import com.wisdomhub.search.domain.dto.HotDTO;
import com.wisdomhub.search.domain.dto.RecallDTO;
import com.wisdomhub.search.domain.dto.SimilarDTO;
import com.wisdomhub.search.domain.vo.ArticleRecallVO;
import com.wisdomhub.search.domain.vo.TagBriefVO;
import com.wisdomhub.search.service.EsIndexService;
import com.wisdomhub.search.service.RecallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecallServiceImpl implements RecallService {

    /** 与搜索用同一套字段权重，多词查询用 */
    private static final List<String> FIELDS = List.of("title^3", "summary^2", "content^1");

    /** MLT 只认字段名，写 "title^3" 会被当成不存在的字段、一个词都取不到，所以另开一份不带权重的 */
    private static final List<String> MLT_FIELDS = List.of("title", "summary", "content");

    /** 只取打分要用的字段：正文整篇不进响应体 */
    private static final List<String> SOURCE_FIELDS = List.of("articleId", "authorId", "title", "summary",
            "tags", "publishTime", "viewCount", "likeCount", "commentCount", "favoriteCount");

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public List<ArticleRecallVO> recall(RecallDTO dto) {
        int size = SearchConstants.clampRecallSize(dto.getLimit());
        boolean hasKeyword = StrUtil.isNotBlank(dto.getKeyword());
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (hasKeyword) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(dto.getKeyword().trim())
                    .fields(FIELDS)
                    .type(TextQueryType.BestFields)
                    .minimumShouldMatch(SearchConstants.MINIMUM_SHOULD_MATCH)));
        }
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            bool.filter(f -> f.terms(t -> t.field("tags.tagId").terms(tv -> tv.value(toValues(dto.getTagIds())))));
        }
        exclude(bool, dto.getExcludeIds());
        // 有关键词就按相关性，纯按标签（或不带条件）召回时相关性没有意义，改按热度给候选
        List<SortOptions> sorts = hasKeyword ? relevanceSorts() : hotSorts();
        return search(bool.build(), sorts, size);
    }

    @Override
    public List<ArticleRecallVO> similar(SimilarDTO dto) {
        int size = SearchConstants.clampRecallSize(dto.getLimit());
        // 种子不在索引里时 MLT 取文档会直接抛错，先问一句更省事
        if (!exists(dto.getArticleId())) {
            return List.of();
        }
        BoolQuery.Builder bool = new BoolQuery.Builder();
        bool.must(m -> m.moreLikeThis(mlt -> mlt
                .fields(MLT_FIELDS)
                .like(l -> l.document(d -> d.index(EsIndexService.ARTICLE_INDEX).id(dto.getArticleId().toString())))
                .minDocFreq(1)
                .minTermFreq(1)
                .minimumShouldMatch(SearchConstants.MLT_MINIMUM_SHOULD_MATCH)));
        exclude(bool, dto.getExcludeIds());
        // 种子文章自己不算相似
        List<String> self = List.of(dto.getArticleId().toString());
        bool.mustNot(m -> m.ids(i -> i.values(self)));
        return search(bool.build(), relevanceSorts(), size);
    }

    @Override
    public List<ArticleRecallVO> hot(HotDTO dto) {
        int size = SearchConstants.clampRecallSize(dto.getLimit());
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (dto.getTagId() != null) {
            bool.filter(f -> f.term(t -> t.field("tags.tagId").value(dto.getTagId().toString())));
        }
        exclude(bool, dto.getExcludeIds());
        return search(bool.build(), hotSorts(), size);
    }

    // ==================== 共用 ====================

    private List<ArticleRecallVO> search(BoolQuery bool, List<SortOptions> sorts, int size) {
        SearchRequest request = new SearchRequest.Builder()
                .index(EsIndexService.ARTICLE_INDEX)
                .query(Query.of(q -> q.bool(bool)))
                .size(size)
                .source(s -> s.filter(f -> f.includes(SOURCE_FIELDS)))
                .sort(sorts)
                .build();
        try {
            SearchResponse<ArticleIndexDoc> response = elasticsearchClient.search(request, ArticleIndexDoc.class);
            List<ArticleRecallVO> items = new ArrayList<>(response.hits().hits().size());
            for (Hit<ArticleIndexDoc> hit : response.hits().hits()) {
                items.add(toVO(hit.source()));
            }
            return items;
        } catch (IOException e) {
            // 连不上 / 超时：依赖不可用（503），不是本服务的内部错误
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "搜索服务暂不可用");
        } catch (ElasticsearchException e) {
            log.error("召回查询失败", e);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "查询失败");
        }
    }

    private boolean exists(Long articleId) {
        try {
            return elasticsearchClient.exists(e -> e
                    .index(EsIndexService.ARTICLE_INDEX)
                    .id(articleId.toString())).value();
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "搜索服务暂不可用");
        }
    }

    private ArticleRecallVO toVO(ArticleIndexDoc doc) {
        List<TagBriefVO> tags = doc.getTags() == null ? List.of() : doc.getTags().stream()
                .map(tag -> TagBriefVO.builder()
                        .tagId(tag.getTagId())
                        .name(tag.getName())
                        .category(tag.getCategory())
                        .build())
                .toList();
        return ArticleRecallVO.builder()
                .articleId(doc.getArticleId())
                .authorId(doc.getAuthorId())
                .title(doc.getTitle())
                .summary(doc.getSummary())
                .tags(tags)
                .publishTime(doc.getPublishTime())
                .viewCount(doc.getViewCount())
                .likeCount(doc.getLikeCount())
                .commentCount(doc.getCommentCount())
                .favoriteCount(doc.getFavoriteCount())
                .build();
    }

    /** 排除调用方已经推过的文章。按 _id 排除，本身不会命中不存在的文档 */
    private void exclude(BoolQuery.Builder bool, List<Long> excludeIds) {
        if (excludeIds == null || excludeIds.isEmpty()) {
            return;
        }
        List<String> ids = excludeIds.stream().map(String::valueOf).toList();
        bool.mustNot(m -> m.ids(i -> i.values(ids)));
    }

    /** 标签是 keyword 字段，值要按字符串传（雪花 ID 的形态与索引写入侧一致） */
    private List<FieldValue> toValues(List<Long> ids) {
        return ids.stream().map(id -> FieldValue.of(String.valueOf(id))).toList();
    }

    /** 同分兜底按发布时间与 ID 倒序，保证同一份候选集顺序稳定 */
    private List<SortOptions> relevanceSorts() {
        return List.of(scoreDesc(), fieldDesc("publishTime"), fieldDesc("articleId"));
    }

    private List<SortOptions> hotSorts() {
        return List.of(fieldDesc("viewCount"), fieldDesc("publishTime"), fieldDesc("articleId"));
    }

    private SortOptions fieldDesc(String field) {
        return SortOptions.of(s -> s.field(f -> f.field(field).order(SortOrder.Desc)));
    }

    private SortOptions scoreDesc() {
        return SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));
    }
}
