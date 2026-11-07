package com.wisdomhub.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.security.context.UserContext;
import com.wisdomhub.interaction.domain.dto.InteractionCountBatchDTO;
import com.wisdomhub.interaction.domain.dto.InteractionCountQueryDTO;
import com.wisdomhub.interaction.domain.entity.ContentFavorite;
import com.wisdomhub.interaction.domain.entity.ContentLike;
import com.wisdomhub.interaction.domain.entity.InteractionCount;
import com.wisdomhub.interaction.domain.vo.InteractionCountBatchVO;
import com.wisdomhub.interaction.domain.vo.InteractionCountVO;
import com.wisdomhub.interaction.domain.vo.InteractionStateVO;
import com.wisdomhub.interaction.mapper.ContentFavoriteMapper;
import com.wisdomhub.interaction.mapper.ContentLikeMapper;
import com.wisdomhub.interaction.mapper.InteractionCountMapper;
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

    @Override
    public InteractionStateVO getState(Integer targetType, Long targetId) {
        int type = requireValidType(targetType);
        if (targetId == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "targetId 不能为空");
        }
        InteractionCount count = interactionCountMapper.selectOne(new LambdaQueryWrapper<InteractionCount>()
                .eq(InteractionCount::getTargetType, (byte) type)
                .eq(InteractionCount::getTargetId, targetId));

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
                .likeCount(count == null ? 0 : nvl(count.getLikeCount()))
                .commentCount(count == null ? 0 : nvl(count.getCommentCount()))
                .favoriteCount(count == null ? 0 : nvl(count.getFavoriteCount()))
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

        Map<String, InteractionCount> countByKey = new HashMap<>();
        for (Map.Entry<Integer, Set<Long>> entry : idsByType.entrySet()) {
            List<InteractionCount> rows = interactionCountMapper.selectList(
                    new LambdaQueryWrapper<InteractionCount>()
                            .select(InteractionCount::getTargetType, InteractionCount::getTargetId,
                                    InteractionCount::getLikeCount, InteractionCount::getCommentCount,
                                    InteractionCount::getFavoriteCount)
                            .eq(InteractionCount::getTargetType, entry.getKey().byteValue())
                            .in(InteractionCount::getTargetId, entry.getValue()));
            for (InteractionCount row : rows) {
                countByKey.put(key(row.getTargetType().intValue(), row.getTargetId()), row);
            }
        }

        List<InteractionCountVO> result = new ArrayList<>(items.size());
        for (InteractionCountQueryDTO item : items) {
            InteractionCount count = countByKey.get(key(item.getTargetType(), item.getTargetId()));
            result.add(InteractionCountVO.builder()
                    .targetType(item.getTargetType())
                    .targetId(item.getTargetId())
                    .likeCount(count == null ? 0 : nvl(count.getLikeCount()))
                    .commentCount(count == null ? 0 : nvl(count.getCommentCount()))
                    .favoriteCount(count == null ? 0 : nvl(count.getFavoriteCount()))
                    .build());
        }
        return InteractionCountBatchVO.builder().items(result).build();
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

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
