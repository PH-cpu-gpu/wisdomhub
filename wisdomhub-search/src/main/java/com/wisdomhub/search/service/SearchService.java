package com.wisdomhub.search.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.search.domain.vo.ArticleSearchVO;
import com.wisdomhub.search.domain.vo.HotWordVO;
import com.wisdomhub.search.domain.vo.QuestionSearchVO;
import com.wisdomhub.search.domain.vo.TagSearchVO;
import com.wisdomhub.search.domain.vo.UserSearchVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 四类搜索。sort 一律走白名单枚举，白名单外的值落回 relevance。
 * 只有文章与问题支持 hot，其余两个的 hot 会落回 relevance。
 */
public interface SearchService {

    /** keyword 为空时等于 "按标签 / 时间浏览" 查 */
    PageResponse<ArticleSearchVO> searchArticles(String keyword, Integer page, Integer pageSize, String sort,
                                                 Long tagId, LocalDateTime startTime, LocalDateTime endTime);

    PageResponse<QuestionSearchVO> searchQuestions(String keyword, Integer page, Integer pageSize, String sort,
                                                   LocalDateTime startTime, LocalDateTime endTime);

    PageResponse<UserSearchVO> searchUsers(String keyword, Integer page, Integer pageSize, String sort);

    PageResponse<TagSearchVO> searchTags(String keyword, Integer page, Integer pageSize, String sort, String category);

    /** 输入建议，只返回建议文本。keyword 为空时回空列表、不打 ES */
    List<String> suggest(String keyword, String type, Integer size);

    /** 热搜词榜：最近 days 天按日统计后合并。Redis 不可用时空列表，不报错 */
    List<HotWordVO> hotWords(Integer days, Integer size);
}
