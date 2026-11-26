package com.wisdomhub.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.security.context.UserContext;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.interaction.domain.dto.InteractionCountBatchDTO;
import com.wisdomhub.interaction.domain.dto.InteractionCountQueryDTO;
import com.wisdomhub.interaction.domain.entity.ContentFavorite;
import com.wisdomhub.interaction.domain.entity.ContentLike;
import com.wisdomhub.interaction.domain.entity.InteractionCount;
import com.wisdomhub.interaction.domain.vo.FavoriteItemVO;
import com.wisdomhub.interaction.domain.vo.InteractionCountBatchVO;
import com.wisdomhub.interaction.domain.vo.InteractionCountVO;
import com.wisdomhub.interaction.domain.vo.InteractionStateVO;
import com.wisdomhub.interaction.mapper.ContentFavoriteMapper;
import com.wisdomhub.interaction.mapper.ContentLikeMapper;
import com.wisdomhub.interaction.mapper.InteractionCountMapper;
import com.wisdomhub.interaction.service.InteractionCountCache;
import com.wisdomhub.interaction.service.InteractionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InteractionQueryServiceImpl implements InteractionQueryService {

    private final InteractionCountMapper interactionCountMapper;
    private final ContentLikeMapper contentLikeMapper;
    private final ContentFavoriteMapper contentFavoriteMapper;
    private final InteractionCountCache interactionCountCache;

    @Override
    public InteractionStateVO getState(Integer targetType, Long targetId) {
        int type = requireValidType(targetType);
        if (targetId == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "targetId 不能为空");
        }
        InteractionCountCache.Counts counts = loadCounts(type, targetId);

        Long userId = UserContext.getUserId();
        boolean liked = userId != null && contentLikeMapper.exists(new LambdaQueryWrapper<ContentLike>()
                .eq(ContentLike::getTargetType, (byte) type)
                .eq(ContentLike::getTargetId, targetId)
                .eq(ContentLike::getUserId, userId));
        boolean collected = userId != null && contentFavoriteMapper.exists(new LambdaQueryWrapper<ContentFavorite>()
                .eq(ContentFavorite::getTargetType, (byte) type)
                .eq(ContentFavorite::getTargetId, targetId)
                .eq(ContentFavorite::getUserId, userId));

        return InteractionStateVO.builder()
                .liked(liked)
                .collected(collected)
                .likeCount(counts.likeCount())
                .commentCount(counts.commentCount())
                .favoriteCount(counts.favoriteCount())
                .build();
    }

    @Override
    public InteractionCountBatchVO batchCounts(InteractionCountBatchDTO request) {
        List<InteractionCountQueryDTO> items = request == null ? null : request.getItems();
        if (items == null || items.isEmpty()) {
            return InteractionCountBatchVO.builder().items(List.of()).build();
        }

        Map<Integer, Set<Long>> idsByType = new LinkedHashMap<>();
        for (InteractionCountQueryDTO item : items) {
            if (item == null) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "批量查询项不能为空");
            }
            int type = requireValidType(item.getTargetType());
            if (item.getTargetId() == null) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "targetId 不能为空");
            }
            idsByType.computeIfAbsent(type, k -> new LinkedHashSet<>()).add(item.getTargetId());
        }

        Map<String, InteractionCountCache.Counts> countByKey = new HashMap<>();
        for (Map.Entry<Integer, Set<Long>> entry : idsByType.entrySet()) {
            int type = entry.getKey();
            Set<Long> ids = entry.getValue();
            Map<Long, InteractionCountCache.Counts> counts = interactionCountCache.getAll(type, ids);

            List<Long> missing = new ArrayList<>(ids.size());
            for (Long id : ids) {
                if (!counts.containsKey(id)) {
                    missing.add(id);
                }
            }
            if (!missing.isEmpty()) {
                List<InteractionCount> rows = interactionCountMapper.selectList(
                        new LambdaQueryWrapper<InteractionCount>()
                                .select(InteractionCount::getTargetType, InteractionCount::getTargetId,
                                        InteractionCount::getLikeCount, InteractionCount::getCommentCount,
                                        InteractionCount::getFavoriteCount)
                                .eq(InteractionCount::getTargetType, (byte) type)
                                .in(InteractionCount::getTargetId, missing));
                Map<Long, InteractionCount> rowByTargetId = new HashMap<>(rows.size());
                for (InteractionCount row : rows) {
                    rowByTargetId.put(row.getTargetId(), row);
                }
                Map<Long, InteractionCountCache.Counts> loaded = new HashMap<>(missing.size());
                for (Long id : missing) {
                    loaded.put(id, InteractionCountCache.Counts.of(rowByTargetId.get(id)));
                }
                interactionCountCache.putAll(type, loaded);
                counts = new HashMap<>(counts);
                counts.putAll(loaded);
            }
            for (Map.Entry<Long, InteractionCountCache.Counts> count : counts.entrySet()) {
                countByKey.put(key(type, count.getKey()), count.getValue());
            }
        }

        List<InteractionCountVO> result = new ArrayList<>(items.size());
        for (InteractionCountQueryDTO item : items) {
            InteractionCountCache.Counts count = countByKey.get(key(item.getTargetType(), item.getTargetId()));
            result.add(InteractionCountVO.builder()
                    .targetType(item.getTargetType())
                    .targetId(item.getTargetId())
                    .likeCount(count == null ? 0 : count.likeCount())
                    .commentCount(count == null ? 0 : count.commentCount())
                    .favoriteCount(count == null ? 0 : count.favoriteCount())
                    .build());
        }
        return InteractionCountBatchVO.builder().items(result).build();
    }

    @Override
    public PageResponse<FavoriteItemVO> pageFavorites(Integer targetType, Integer page, Integer pageSize) {
        Long userId = CurrentUserUtil.id();
        Byte type = targetType == null ? null : (byte) requireValidType(targetType);

        Page<ContentFavorite> result = contentFavoriteMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<ContentFavorite>()
                        .eq(ContentFavorite::getUserId, userId)
                        .eq(type != null, ContentFavorite::getTargetType, type)
                        .orderByDesc(ContentFavorite::getCreateTime)
                        .orderByDesc(ContentFavorite::getId));

        List<FavoriteItemVO> items = result.getRecords().stream()
                .map(row -> FavoriteItemVO.builder()
                        .targetType(row.getTargetType().intValue())
                        .targetId(row.getTargetId())
                        .createTime(row.getCreateTime())
                        .build())
                .toList();
        return PageUtil.toResponse(result, items);
    }

    private InteractionCountCache.Counts loadCounts(int targetType, Long targetId) {
        InteractionCountCache.Counts cached = interactionCountCache.get(targetType, targetId);
        if (cached != null) {
            return cached;
        }
        InteractionCount row = interactionCountMapper.selectOne(new LambdaQueryWrapper<InteractionCount>()
                .eq(InteractionCount::getTargetType, (byte) targetType)
                .eq(InteractionCount::getTargetId, targetId));
        InteractionCountCache.Counts counts = InteractionCountCache.Counts.of(row);
        interactionCountCache.put(targetType, targetId, counts);
        return counts;
    }

    private int requireValidType(Integer targetType) {
        if (targetType == null || !ContentTargetTypeConstants.isValid(targetType)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "targetType 不合法");
        }
        return targetType;
    }

    private String key(int targetType, Long targetId) {
        return targetType + ":" + targetId;
    }
}
