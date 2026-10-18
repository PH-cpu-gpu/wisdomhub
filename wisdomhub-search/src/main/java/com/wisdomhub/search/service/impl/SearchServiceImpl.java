package com.wisdomhub.search.service.impl;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.DateRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlighterEncoder;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.search.constants.SearchConstants;
import com.wisdomhub.search.mq.BehaviorEventPublisher;
import com.wisdomhub.search.domain.document.ArticleIndexDoc;
import com.wisdomhub.search.domain.document.QuestionIndexDoc;
import com.wisdomhub.search.domain.document.TagIndexDoc;
import com.wisdomhub.search.domain.document.UserIndexDoc;
import com.wisdomhub.search.domain.vo.ArticleSearchVO;
import com.wisdomhub.search.domain.vo.HotWordVO;
import com.wisdomhub.search.domain.vo.QuestionSearchVO;
import com.wisdomhub.search.domain.vo.TagBriefVO;
import com.wisdomhub.search.domain.vo.TagSearchVO;
import com.wisdomhub.search.domain.vo.UserSearchVO;
import com.wisdomhub.search.service.EsIndexService;
import com.wisdomhub.search.service.HotWordService;
import com.wisdomhub.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    /** 字段权重在这里，mapping 只负责把三个字段拆开 */
    private static final List<String> ARTICLE_FIELDS = List.of("title^3", "summary^2", "content^1");
    private static final List<String> QUESTION_FIELDS = List.of("title^3", "content^1");
    private static final List<String> USER_FIELDS = List.of("username^3", "bio^1");
    private static final List<String> TAG_FIELDS = List.of("name^3", "description^1");

    /** suggest 请求里给这个 suggester 起的名字，取回结果时按它索引 */
    private static final String SUGGESTER_NAME = "wh_suggest";

    private final ElasticsearchClient elasticsearchClient;

    private final HotWordService hotWordService;

    private final BehaviorEventPublisher behaviorEventPublisher;

    // ==================== 文章 ====================

    @Override
    public PageResponse<ArticleSearchVO> searchArticles(String keyword, Integer page, Integer pageSize, String sort,
                                                        Long tagId, LocalDateTime startTime, LocalDateTime endTime) {
        hotWordService.record(keyword);
        behaviorEventPublisher.search(keyword, tagId);
        int pageNo = SearchConstants.clampPage(page);
        int size = SearchConstants.clampPageSize(pageSize);
        Query query = buildQuery(keyword, ARTICLE_FIELDS, bool -> {
            if (tagId != null) {
                bool.filter(f -> f.term(t -> t.field("tags.tagId").value(String.valueOf(tagId))));
            }
            addPublishTimeRange(bool, startTime, endTime);
        });
        SearchRequest request = baseRequest(EsIndexService.ARTICLE_INDEX, query, articleSorts(sort), pageNo, size)
                .highlight(highlightBuilder()
                        .fields("title", f -> f.numberOfFragments(0))
                        .fields("summary", f -> f.numberOfFragments(0))
                        .fields("content", f -> f.numberOfFragments(1).fragmentSize(SearchConstants.FRAGMENT_SIZE))
                        .build())
                .build();
        SearchResponse<ArticleIndexDoc> response = execute(request, ArticleIndexDoc.class);
        List<ArticleSearchVO> items = response.hits().hits().stream().map(this::toArticleVO).toList();
        return toPage(response, items, pageNo, size);
    }

    private ArticleSearchVO toArticleVO(Hit<ArticleIndexDoc> hit) {
        ArticleIndexDoc doc = hit.source();
        List<TagBriefVO> tags = doc.getTags() == null ? List.of() : doc.getTags().stream()
                .map(tag -> TagBriefVO.builder()
                        .tagId(tag.getTagId())
                        .name(tag.getName())
                        .category(tag.getCategory())
                        .build())
                .toList();
        return ArticleSearchVO.builder()
                .articleId(doc.getArticleId())
                .authorId(doc.getAuthorId())
                .title(doc.getTitle())
                .summary(doc.getSummary())
                .highlight(toHighlight(hit))
                .tags(tags)
                .publishTime(doc.getPublishTime())
                .viewCount(doc.getViewCount())
                .likeCount(doc.getLikeCount())
                .commentCount(doc.getCommentCount())
                .favoriteCount(doc.getFavoriteCount())
                .build();
    }

    private List<SortOptions> articleSorts(String sort) {
        return switch (normalizeSort(sort)) {
            case SearchConstants.SORT_NEW -> List.of(fieldDesc("publishTime"), fieldDesc("articleId"));
            case SearchConstants.SORT_HOT -> List.of(fieldDesc("viewCount"), fieldDesc("publishTime"), fieldDesc("articleId"));
            // 相关性同分时用发布时间兜底，保证分页稳定
            default -> List.of(scoreDesc(), fieldDesc("publishTime"), fieldDesc("articleId"));
        };
    }

    // ==================== 问题 ====================

    @Override
    public PageResponse<QuestionSearchVO> searchQuestions(String keyword, Integer page, Integer pageSize, String sort,
                                                          LocalDateTime startTime, LocalDateTime endTime) {
        hotWordService.record(keyword);
        int pageNo = SearchConstants.clampPage(page);
        int size = SearchConstants.clampPageSize(pageSize);
        Query query = buildQuery(keyword, QUESTION_FIELDS, bool -> addPublishTimeRange(bool, startTime, endTime, "createTime"));
        SearchRequest request = baseRequest(EsIndexService.QUESTION_INDEX, query, questionSorts(sort), pageNo, size)
                .highlight(highlightBuilder()
                        .fields("title", f -> f.numberOfFragments(0))
                        .fields("content", f -> f.numberOfFragments(1).fragmentSize(SearchConstants.FRAGMENT_SIZE))
                        .build())
                .build();
        SearchResponse<QuestionIndexDoc> response = execute(request, QuestionIndexDoc.class);
        List<QuestionSearchVO> items = response.hits().hits().stream().map(this::toQuestionVO).toList();
        return toPage(response, items, pageNo, size);
    }

    private QuestionSearchVO toQuestionVO(Hit<QuestionIndexDoc> hit) {
        QuestionIndexDoc doc = hit.source();
        return QuestionSearchVO.builder()
                .questionId(doc.getQuestionId())
                .userId(doc.getUserId())
                .title(doc.getTitle())
                .content(doc.getContent())
                .highlight(toHighlight(hit))
                .answerCount(doc.getAnswerCount())
                .favoriteCount(doc.getFavoriteCount())
                .acceptedAnswerId(doc.getAcceptedAnswerId())
                .createTime(doc.getCreateTime())
                .updateTime(doc.getUpdateTime())
                .build();
    }

    private List<SortOptions> questionSorts(String sort) {
        return switch (normalizeSort(sort)) {
            case SearchConstants.SORT_NEW -> List.of(fieldDesc("createTime"), fieldDesc("questionId"));
            case SearchConstants.SORT_HOT -> List.of(fieldDesc("answerCount"), fieldDesc("createTime"), fieldDesc("questionId"));
            default -> List.of(scoreDesc(), fieldDesc("createTime"), fieldDesc("questionId"));
        };
    }

    // ==================== 用户 ====================

    @Override
    public PageResponse<UserSearchVO> searchUsers(String keyword, Integer page, Integer pageSize, String sort) {
        hotWordService.record(keyword);
        int pageNo = SearchConstants.clampPage(page);
        int size = SearchConstants.clampPageSize(pageSize);
        Query query = buildQuery(keyword, USER_FIELDS, bool -> { });
        List<SortOptions> sorts = SearchConstants.SORT_NEW.equals(normalizeSort(sort))
                ? List.of(fieldDesc("createTime"), fieldDesc("userId"))
                : List.of(scoreDesc(), fieldDesc("userId"));
        SearchRequest request = baseRequest(EsIndexService.USER_INDEX, query, sorts, pageNo, size)
                .highlight(highlightBuilder()
                        .fields("username", f -> f.numberOfFragments(0))
                        .fields("bio", f -> f.numberOfFragments(1).fragmentSize(SearchConstants.FRAGMENT_SIZE))
                        .build())
                .build();
        SearchResponse<UserIndexDoc> response = execute(request, UserIndexDoc.class);
        List<UserSearchVO> items = response.hits().hits().stream().map(this::toUserVO).toList();
        return toPage(response, items, pageNo, size);
    }

    private UserSearchVO toUserVO(Hit<UserIndexDoc> hit) {
        UserIndexDoc doc = hit.source();
        return UserSearchVO.builder()
                .userId(doc.getUserId())
                .username(doc.getUsername())
                .avatar(doc.getAvatar())
                .bio(doc.getBio())
                .highlight(toHighlight(hit))
                .createTime(doc.getCreateTime())
                .updateTime(doc.getUpdateTime())
                .build();
    }

    // ==================== 标签 ====================

    @Override
    public PageResponse<TagSearchVO> searchTags(String keyword, Integer page, Integer pageSize, String sort, String category) {
        hotWordService.record(keyword);
        behaviorEventPublisher.search(keyword, null);
        int pageNo = SearchConstants.clampPage(page);
        int size = SearchConstants.clampPageSize(pageSize);
        Query query = buildQuery(keyword, TAG_FIELDS, bool -> {
            if (StrUtil.isNotBlank(category)) {
                bool.filter(f -> f.term(t -> t.field("category").value(category.trim())));
            }
        });
        List<SortOptions> sorts = SearchConstants.SORT_NEW.equals(normalizeSort(sort))
                ? List.of(fieldDesc("createTime"), fieldDesc("tagId"))
                : List.of(scoreDesc(), fieldDesc("tagId"));
        SearchRequest request = baseRequest(EsIndexService.TAG_INDEX, query, sorts, pageNo, size)
                .highlight(highlightBuilder()
                        .fields("name", f -> f.numberOfFragments(0))
                        .fields("description", f -> f.numberOfFragments(0))
                        .build())
                .build();
        SearchResponse<TagIndexDoc> response = execute(request, TagIndexDoc.class);
        List<TagSearchVO> items = response.hits().hits().stream().map(this::toTagVO).toList();
        return toPage(response, items, pageNo, size);
    }

    private TagSearchVO toTagVO(Hit<TagIndexDoc> hit) {
        TagIndexDoc doc = hit.source();
        return TagSearchVO.builder()
                .tagId(doc.getTagId())
                .name(doc.getName())
                .category(doc.getCategory())
                .description(doc.getDescription())
                .highlight(toHighlight(hit))
                .createTime(doc.getCreateTime())
                .updateTime(doc.getUpdateTime())
                .build();
    }

    // ==================== 输入建议 / 热搜词 ====================

    @Override
    public List<String> suggest(String keyword, String type, Integer size) {
        // 输入框清空时不打 ES：空 prefix 会把整张索引的建议全捞回来
        if (StrUtil.isBlank(keyword)) {
            return List.of();
        }
        SearchRequest request = new SearchRequest.Builder()
                .index(suggestIndex(type))
                .suggest(s -> s.suggesters(SUGGESTER_NAME, f -> f
                        .prefix(keyword.trim())
                        .completion(c -> c.field("suggest")
                                .size(SearchConstants.clampSuggestSize(size))
                                .skipDuplicates(true))))
                .source(s -> s.fetch(false))
                .build();
        try {
            return extractSuggestions(elasticsearchClient.search(request, Void.class));
        } catch (IOException | ElasticsearchException e) {
            // 联想是输入框上的装饰，打挂它只是噪声，所以降级成空列表
            log.error("输入建议查询失败, index={}", request.index(), e);
            return List.of();
        }
    }

    @Override
    public List<HotWordVO> hotWords(Integer days, Integer size) {
        return hotWordService.top(days, size);
    }

    private List<String> extractSuggestions(SearchResponse<Void> response) {
        List<Suggestion<Void>> suggestions = response.suggest().get(SUGGESTER_NAME);
        if (suggestions == null) {
            return List.of();
        }
        LinkedHashSet<String> texts = new LinkedHashSet<>();
        for (Suggestion<Void> suggestion : suggestions) {
            if (!suggestion.isCompletion()) {
                continue;
            }
            for (CompletionSuggestOption<Void> option : suggestion.completion().options()) {
                if (StrUtil.isNotBlank(option.text())) {
                    texts.add(option.text());
                }
            }
        }
        return List.copyOf(texts);
    }

    /** 白名单外的 type 落回 article */
    private String suggestIndex(String type) {
        return switch (type == null ? "" : type.trim().toLowerCase(Locale.ROOT)) {
            case "question" -> EsIndexService.QUESTION_INDEX;
            case "user" -> EsIndexService.USER_INDEX;
            case "tag" -> EsIndexService.TAG_INDEX;
            default -> EsIndexService.ARTICLE_INDEX;
        };
    }

    // ==================== 共用 ====================

    /** 关键词为空时只留过滤条件：bool 里没有子句就等于 match_all，所以"按标签浏览"不用另写一条分支 */
    private Query buildQuery(String keyword, List<String> fields, Consumer<BoolQuery.Builder> filterApplier) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (StrUtil.isNotBlank(keyword)) {
            String trimmed = keyword.trim();
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(trimmed)
                    .fields(fields)
                    .type(TextQueryType.BestFields)
                    .minimumShouldMatch(SearchConstants.MINIMUM_SHOULD_MATCH)));
        }
        filterApplier.accept(bool);
        return Query.of(q -> q.bool(bool.build()));
    }

    private void addPublishTimeRange(BoolQuery.Builder bool, LocalDateTime startTime, LocalDateTime endTime) {
        addPublishTimeRange(bool, startTime, endTime, "publishTime");
    }

    private void addPublishTimeRange(BoolQuery.Builder bool, LocalDateTime startTime, LocalDateTime endTime, String field) {
        if (startTime == null && endTime == null) {
            return;
        }
        DateRangeQuery.Builder range = new DateRangeQuery.Builder().field(field);
        if (startTime != null) {
            range.gte(esDate(startTime));
        }
        if (endTime != null) {
            range.lte(esDate(endTime));
        }
        DateRangeQuery built = range.build();
        bool.filter(f -> f.range(r -> r.date(built)));
    }

    /** 库里存的是 UTC 墙钟，转成带 Z 的串交给 ES 解析*/
    private String esDate(LocalDateTime time) {
        return time.toInstant(ZoneOffset.UTC).toString();
    }

    private SearchRequest.Builder baseRequest(String index, Query query, List<SortOptions> sorts, int page, int size) {
        return new SearchRequest.Builder()
                .index(index)
                .query(query)
                .from(SearchConstants.clampFrom(page, size))
                .size(size)
                // 默认可只数到 10000，分页的 total 要精确
                .trackTotalHits(t -> t.enabled(true))
                // 正文整篇不进响应体，只从 highlight 里以片段出去（存储留、传输不留）
                .source(s -> s.filter(f -> f.excludes("content")))
                .sort(sorts);
    }

    /** 高亮标签 + encoder: html 必须一起给，见 SearchConstants 的说明；各接口只用再补 fields */
    private Highlight.Builder highlightBuilder() {
        return new Highlight.Builder()
                .preTags(SearchConstants.HIGHLIGHT_PRE_TAG)
                .postTags(SearchConstants.HIGHLIGHT_POST_TAG)
                .encoder(HighlighterEncoder.Html);
    }

    /** 没命中的字段不出现；命中多个片段时只取第一个（content 只要一片，title/summary 是整段） */
    private Map<String, String> toHighlight(Hit<?> hit) {
        Map<String, List<String>> raw = hit.highlight();
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((field, fragments) -> {
            if (fragments != null && !fragments.isEmpty()) {
                result.put(field, fragments.getFirst());
            }
        });
        return result;
    }

    private String normalizeSort(String sort) {
        return StrUtil.isBlank(sort) ? SearchConstants.SORT_RELEVANCE : sort.trim().toLowerCase(Locale.ROOT);
    }

    private SortOptions fieldDesc(String field) {
        return SortOptions.of(s -> s.field(f -> f.field(field).order(SortOrder.Desc)));
    }

    private SortOptions scoreDesc() {
        return SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));
    }

    private <T> SearchResponse<T> execute(SearchRequest request, Class<T> docType) {
        try {
            return elasticsearchClient.search(request, docType);
        } catch (IOException e) {
            // 连不上 / 超时：这是依赖不可用（503），不是本服务的内部错误
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "搜索服务暂不可用");
        } catch (ElasticsearchException e) {
            log.error("ES 查询失败, index={}", request.index(), e);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "查询失败");
        }
    }

    private <T> PageResponse<T> toPage(SearchResponse<?> response, List<T> items, int page, int size) {
        TotalHits totalHits = response.hits().total();
        long total = totalHits == null ? items.size() : totalHits.value();
        return PageResponse.<T>builder()
                .total((int) total)
                .pages((int) ((total + size - 1) / size))
                .page(page)
                .pageSize(size)
                .items(items)
                .build();
    }
}
