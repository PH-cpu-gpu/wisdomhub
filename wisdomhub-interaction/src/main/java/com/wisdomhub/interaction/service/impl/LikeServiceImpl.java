package com.wisdomhub.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.feign.dto.TargetInfoDTO;
import com.wisdomhub.common.mq.constants.BehaviorTypeConstants;
import com.wisdomhub.common.redis.constants.RedisTtl;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.interaction.constants.InteractionRedisKeyConstants;
import com.wisdomhub.interaction.domain.entity.ContentLike;
import com.wisdomhub.interaction.mapper.ContentLikeMapper;
import com.wisdomhub.interaction.mapper.InteractionCountMapper;
import com.wisdomhub.interaction.service.InteractionCountCache;
import com.wisdomhub.interaction.service.InteractionEventPublisher;
import com.wisdomhub.interaction.service.InteractionGate;
import com.wisdomhub.interaction.service.LikeService;
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
public class LikeServiceImpl implements LikeService {

    private static final Set<Integer> SUPPORTED_TARGET_TYPES = Set.of(
            ContentTargetTypeConstants.ARTICLE,
            ContentTargetTypeConstants.ANSWER,
            ContentTargetTypeConstants.VIDEO);

    private final ContentLikeMapper contentLikeMapper;
    private final InteractionCountMapper interactionCountMapper;
    private final TargetValidator targetValidator;
    private final InteractionGate interactionGate;
    private final InteractionEventPublisher eventPublisher;
    private final InteractionCountCache interactionCountCache;

    @Override
    @Transactional
    public int like(Integer targetType, Long targetId) {
        int type = resolveTargetType(targetType);
        Long userId = CurrentUserUtil.id();
        String stateKey = InteractionRedisKeyConstants.likeState(type, userId, targetId);
        // 冷却中拒绝、已赞幂等，只有无状态才去抢状态
        InteractionGate.LikeState state = interactionGate.likeState(stateKey);
        if (state == InteractionGate.LikeState.COOLING) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        if (state == InteractionGate.LikeState.LIKED) {
            return readLikeCount(type, targetId);
        }
        targetValidator.requireVisible(type, targetId);

        if (!interactionGate.markLiked(stateKey)) {
            // 并发请求先抢到了状态：按改写后的状态处理
            if (interactionGate.likeState(stateKey) == InteractionGate.LikeState.COOLING) {
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
            }
            return readLikeCount(type, targetId);
        }

        try {
            contentLikeMapper.insert(ContentLike.builder()
                    .targetType((byte) type)
                    .targetId(targetId)
                    .userId(userId)
                    .build());
        } catch (DuplicateKeyException e) {
            // Redis 丢过状态但关系行还在：幂等返回，不加计数
            return readLikeCount(type, targetId);
        }

        TargetInfoDTO target;
        try {
            // 插入后再校验一次：校验之后被并发下架/删除时回滚这次点赞
            target = targetValidator.requireVisible(type, targetId);
        } catch (RuntimeException e) {
            interactionGate.clear(stateKey);
            throw e;
        }

        interactionCountMapper.addLikeCount(IdWorker.getId(), type, targetId, 1,
                LocalDateTime.now(ZoneOffset.UTC));
        interactionCountCache.evictAfterCommit(type, targetId);
        if (type == ContentTargetTypeConstants.ARTICLE) {
            eventPublisher.articleLiked(targetId, userId, target.getOwnerId());
            eventPublisher.behaviorRecorded(type, targetId, userId, BehaviorTypeConstants.LIKE);
        } else if (type == ContentTargetTypeConstants.ANSWER) {
            eventPublisher.answerLiked(targetId, userId, target.getOwnerId());
        } else if (type == ContentTargetTypeConstants.VIDEO) {
            eventPublisher.videoLiked(targetId, userId, target.getOwnerId());
            eventPublisher.behaviorRecorded(type, targetId, userId, BehaviorTypeConstants.LIKE);
        }
        return readLikeCount(type, targetId);
    }

    @Override
    @Transactional
    public int unlike(Integer targetType, Long targetId) {
        int type = resolveTargetType(targetType);
        Long userId = CurrentUserUtil.id();
        String stateKey = InteractionRedisKeyConstants.likeState(type, userId, targetId);
        // 冷却中拒绝重复取消，取消接口自身也不能被刷
        if (interactionGate.likeState(stateKey) == InteractionGate.LikeState.COOLING) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        // 一条命令完成「撤已赞 + 置冷却」，不留旧流程 clear 与 cooldown 之间的窗口
        interactionGate.markCooling(stateKey, RedisTtl.LIKE_COOLDOWN);
        targetValidator.requireVisible(type, targetId);

        int deleted = contentLikeMapper.delete(new LambdaQueryWrapper<ContentLike>()
                .eq(ContentLike::getTargetType, (byte) type)
                .eq(ContentLike::getTargetId, targetId)
                .eq(ContentLike::getUserId, userId));
        if (deleted == 0) {
            return readLikeCount(type, targetId);
        }
        interactionCountMapper.addLikeCount(IdWorker.getId(), type, targetId, -1,
                LocalDateTime.now(ZoneOffset.UTC));
        interactionCountCache.evictAfterCommit(type, targetId);
        return readLikeCount(type, targetId);
    }

    private int resolveTargetType(Integer targetType) {
        if (targetType == null || !SUPPORTED_TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "点赞目标类型不合法");
        }
        return targetType;
    }

    private int readLikeCount(int targetType, Long targetId) {
        Integer count = interactionCountMapper.selectLikeCount(targetType, targetId);
        return count == null ? 0 : count;
    }
}
