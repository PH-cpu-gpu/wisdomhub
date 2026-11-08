package com.wisdomhub.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.mq.constants.BehaviorTypeConstants;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.interaction.constants.InteractionRedisKeyConstants;
import com.wisdomhub.interaction.domain.entity.ContentFavorite;
import com.wisdomhub.interaction.mapper.ContentFavoriteMapper;
import com.wisdomhub.interaction.mapper.InteractionCountMapper;
import com.wisdomhub.interaction.service.FavoriteService;
import com.wisdomhub.interaction.service.InteractionEventPublisher;
import com.wisdomhub.interaction.service.InteractionGate;
import com.wisdomhub.interaction.service.TargetValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private static final Set<Integer> SUPPORTED_TARGET_TYPES = Set.of(
            ContentTargetTypeConstants.ARTICLE,
            ContentTargetTypeConstants.QUESTION,
            ContentTargetTypeConstants.VIDEO);

    private final ContentFavoriteMapper contentFavoriteMapper;
    private final InteractionCountMapper interactionCountMapper;
    private final TargetValidator targetValidator;
    private final InteractionGate interactionGate;
    private final InteractionEventPublisher eventPublisher;

    @Override
    @Transactional
    public int favorite(Integer targetType, Long targetId) {
        int type = resolveTargetType(targetType);
        Long userId = CurrentUserUtil.id();
        targetValidator.requireVisible(type, targetId);

        String gateKey = InteractionRedisKeyConstants.favoriteGate(type, userId, targetId);
        if (!interactionGate.pass(gateKey)) {
            return readFavoriteCount(type, targetId);
        }

        try {
            contentFavoriteMapper.insert(ContentFavorite.builder()
                    .targetType((byte) type)
                    .targetId(targetId)
                    .userId(userId)
                    .build());
        } catch (DuplicateKeyException e) {
            // Redis 丢过 key 但关系行还在：幂等返回，不加计数
            return readFavoriteCount(type, targetId);
        }

        try {
            // 插入后再校验一次：校验之后被并发下架/删除时回滚这次收藏
            targetValidator.requireVisible(type, targetId);
        } catch (RuntimeException e) {
            interactionGate.clear(gateKey);
            throw e;
        }

        interactionCountMapper.addFavoriteCount(IdWorker.getId(), type, targetId, 1,
                LocalDateTime.now(ZoneOffset.UTC));
        if (type == ContentTargetTypeConstants.ARTICLE) {
            eventPublisher.behaviorRecorded(type, targetId, userId, BehaviorTypeConstants.COLLECT);
        }
        return readFavoriteCount(type, targetId);
    }

    @Override
    @Transactional
    public int unfavorite(Integer targetType, Long targetId) {
        int type = resolveTargetType(targetType);
        Long userId = CurrentUserUtil.id();
        targetValidator.requireVisible(type, targetId);
        // 先撤 gate：不撤的话用户在这个窗口内没法再收藏回来
        interactionGate.clear(InteractionRedisKeyConstants.favoriteGate(type, userId, targetId));

        int deleted = contentFavoriteMapper.delete(new LambdaQueryWrapper<ContentFavorite>()
                .eq(ContentFavorite::getTargetType, (byte) type)
                .eq(ContentFavorite::getTargetId, targetId)
                .eq(ContentFavorite::getUserId, userId));
        if (deleted == 0) {
            return readFavoriteCount(type, targetId);
        }
        interactionCountMapper.addFavoriteCount(IdWorker.getId(), type, targetId, -1,
                LocalDateTime.now(ZoneOffset.UTC));
        return readFavoriteCount(type, targetId);
    }

    private int resolveTargetType(Integer targetType) {
        if (targetType == null || !SUPPORTED_TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "收藏目标类型不合法");
        }
        return targetType;
    }

    private int readFavoriteCount(int targetType, Long targetId) {
        Integer count = interactionCountMapper.selectFavoriteCount(targetType, targetId);
        return count == null ? 0 : count;
    }
}
