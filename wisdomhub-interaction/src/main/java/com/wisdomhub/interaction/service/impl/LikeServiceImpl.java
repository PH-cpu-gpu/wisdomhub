package com.wisdomhub.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.interaction.constants.InteractionRedisKeyConstants;
import com.wisdomhub.interaction.domain.entity.ContentLike;
import com.wisdomhub.interaction.mapper.ContentLikeMapper;
import com.wisdomhub.interaction.mapper.InteractionCountMapper;
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
            ContentTargetTypeConstants.ANSWER);

    private final ContentLikeMapper contentLikeMapper;
    private final InteractionCountMapper interactionCountMapper;
    private final TargetValidator targetValidator;
    private final InteractionGate interactionGate;

    @Override
    @Transactional
    public int like(Integer targetType, Long targetId) {
        int type = resolveTargetType(targetType);
        Long userId = CurrentUserUtil.id();
        targetValidator.requireVisible(type, targetId);

        String gateKey = InteractionRedisKeyConstants.likeGate(type, userId, targetId);
        if (!interactionGate.pass(gateKey)) {
            return readLikeCount(type, targetId);
        }

        try {
            contentLikeMapper.insert(ContentLike.builder()
                    .targetType((byte) type)
                    .targetId(targetId)
                    .userId(userId)
                    .build());
        } catch (DuplicateKeyException e) {
            // Redis 丢过 key 但关系行还在：幂等返回，不加计数
            return readLikeCount(type, targetId);
        }

        try {
            // 插入后再校验一次：校验之后被并发下架/删除时回滚这次点赞
            targetValidator.requireVisible(type, targetId);
        } catch (RuntimeException e) {
            interactionGate.clear(gateKey);
            throw e;
        }

        interactionCountMapper.addLikeCount(IdWorker.getId(), type, targetId, 1,
                LocalDateTime.now(ZoneOffset.UTC));
        return readLikeCount(type, targetId);
    }

    @Override
    @Transactional
    public int unlike(Integer targetType, Long targetId) {
        int type = resolveTargetType(targetType);
        Long userId = CurrentUserUtil.id();
        targetValidator.requireVisible(type, targetId);
        // 先撤 gate：不撤的话用户在这个窗口内没法再点回来
        interactionGate.clear(InteractionRedisKeyConstants.likeGate(type, userId, targetId));

        int deleted = contentLikeMapper.delete(new LambdaQueryWrapper<ContentLike>()
                .eq(ContentLike::getTargetType, (byte) type)
                .eq(ContentLike::getTargetId, targetId)
                .eq(ContentLike::getUserId, userId));
        if (deleted == 0) {
            return readLikeCount(type, targetId);
        }
        interactionCountMapper.addLikeCount(IdWorker.getId(), type, targetId, -1,
                LocalDateTime.now(ZoneOffset.UTC));
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
