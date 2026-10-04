package com.wisdomhub.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.user.api.ArticleFeign;
import com.wisdomhub.user.api.dto.ArticleBriefDTO;
import com.wisdomhub.user.domain.entity.UserBrowseHistory;
import com.wisdomhub.user.domain.vo.BrowseHistoryVO;
import com.wisdomhub.user.mapper.UserBrowseHistoryMapper;
import com.wisdomhub.user.service.BrowseHistoryService;
import com.wisdomhub.user.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BrowseHistoryServiceImpl implements BrowseHistoryService {

    private final UserBrowseHistoryMapper userBrowseHistoryMapper;
    private final ArticleFeign articleFeign;

    @Override
    public void record(Long articleId) {
        Long userId = CurrentUserUtil.id();
        // 已存在就只更新最近浏览时间
        int updated = userBrowseHistoryMapper.update(new UserBrowseHistory(),
                new LambdaUpdateWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUserId, userId)
                        .eq(UserBrowseHistory::getArticleId, articleId));
        if (updated > 0) {
            return;
        }
        try {
            userBrowseHistoryMapper.insert(UserBrowseHistory.builder()
                    .userId(userId)
                    .articleId(articleId)
                    .build());
        } catch (DuplicateKeyException ignored) {
            // 并发下被别的请求抢先插入了，结果一致，当成功
        }
    }

    @Override
    public PageResponse<BrowseHistoryVO> list(Integer page, Integer pageSize) {
        Page<UserBrowseHistory> result = userBrowseHistoryMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUserId, CurrentUserUtil.id())
                        .orderByDesc(UserBrowseHistory::getUpdateTime));
        List<Long> articleIds = result.getRecords().stream().map(UserBrowseHistory::getArticleId).toList();
        Map<Long, ArticleBriefDTO> briefById = briefById(articleIds);
        // 文章已删除/下架时 content 侧查不到，title 留 null 让前端显示"文章已删除"；
        List<BrowseHistoryVO> items = result.getRecords().stream()
                .map(history -> {
                    ArticleBriefDTO brief = briefById.get(history.getArticleId());
                    return BrowseHistoryVO.builder()
                            .articleId(history.getArticleId())
                            .title(brief == null ? null : brief.getTitle())
                            .summary(brief == null ? null : brief.getSummary())
                            .updateTime(history.getUpdateTime())
                            .build();
                })
                .toList();
        return PageUtil.toResponse(result, items);
    }

    @Override
    public void delete(Long articleId) {
        // 删 0 行也当成功，取关同一个套路
        userBrowseHistoryMapper.delete(new LambdaQueryWrapper<UserBrowseHistory>()
                .eq(UserBrowseHistory::getUserId, CurrentUserUtil.id())
                .eq(UserBrowseHistory::getArticleId, articleId));
    }

    @Override
    public void clear() {
        userBrowseHistoryMapper.delete(new LambdaQueryWrapper<UserBrowseHistory>()
                .eq(UserBrowseHistory::getUserId, CurrentUserUtil.id()));
    }

    /** IN 查询不保证返回顺序，收进 Map 让调用方按自己的顺序取 */
    private Map<Long, ArticleBriefDTO> briefById(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ArticleBriefDTO> briefById = new HashMap<>();
        for (ArticleBriefDTO brief : articleFeign.listBrief(articleIds)) {
            briefById.put(brief.getArticleId(), brief);
        }
        return briefById;
    }
}
