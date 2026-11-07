package com.wisdomhub.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.constants.ContentTargetTypeConstants;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.feign.dto.TargetInfoDTO;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.OwnerUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.interaction.constants.CommentConstants;
import com.wisdomhub.interaction.domain.dto.CommentCreateDTO;
import com.wisdomhub.interaction.domain.entity.Comment;
import com.wisdomhub.interaction.domain.vo.CommentVO;
import com.wisdomhub.interaction.mapper.CommentMapper;
import com.wisdomhub.interaction.mapper.InteractionCountMapper;
import com.wisdomhub.interaction.service.CommentService;
import com.wisdomhub.interaction.service.InteractionEventPublisher;
import com.wisdomhub.interaction.service.TargetValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private static final Set<Integer> SUPPORTED_TARGET_TYPES = Set.of(
            ContentTargetTypeConstants.ARTICLE,
            ContentTargetTypeConstants.ANSWER,
            ContentTargetTypeConstants.QUESTION);

    private final CommentMapper commentMapper;
    private final InteractionCountMapper interactionCountMapper;
    private final TargetValidator targetValidator;
    private final InteractionEventPublisher eventPublisher;

    @Override
    @Transactional
    public Long createComment(CommentCreateDTO dto) {
        Long userId = CurrentUserUtil.id();
        int targetType = resolveTargetType(dto.getTargetType());
        Long targetId = dto.getTargetId();
        TargetInfoDTO target = targetValidator.requireVisible(targetType, targetId);
        Long parentId = normalizeParentId(dto.getParentId(), targetType, targetId);

        Comment comment = Comment.builder()
                .targetType((byte) targetType)
                .targetId(targetId)
                .userId(userId)
                .parentId(parentId)
                .content(dto.getContent().trim())
                .deleted((byte) CommentConstants.NOT_DELETED)
                .build();
        commentMapper.insert(comment);

        interactionCountMapper.addCommentCount(IdWorker.getId(), targetType, targetId, 1,
                LocalDateTime.now(ZoneOffset.UTC));
        eventPublisher.commentCreated(comment.getId(), targetType, targetId, userId, target.getOwnerId());
        return comment.getId();
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        CurrentUserUtil.id();
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted() == (byte) CommentConstants.DELETED) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "评论不存在");
        }
        OwnerUtil.assertOwner(comment.getUserId());

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int affected = commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .set(Comment::getDeleted, (byte) CommentConstants.DELETED)
                .set(Comment::getUpdateTime, now)
                .eq(Comment::getDeleted, (byte) CommentConstants.NOT_DELETED)
                .and(w -> w.eq(Comment::getId, commentId).or().eq(Comment::getParentId, commentId)));
        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "评论不存在");
        }
        interactionCountMapper.addCommentCount(IdWorker.getId(), comment.getTargetType(),
                comment.getTargetId(), -affected, now);
    }

    @Override
    public PageResponse<CommentVO> pageComments(Integer targetType, Long targetId,
                                                Integer page, Integer pageSize) {
        int type = resolveTargetType(targetType);
        Page<Comment> rootPage = commentMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getTargetType, (byte) type)
                        .eq(Comment::getTargetId, targetId)
                        .isNull(Comment::getParentId)
                        .eq(Comment::getDeleted, (byte) CommentConstants.NOT_DELETED)
                        .orderByAsc(Comment::getCreateTime)
                        .orderByAsc(Comment::getId));

        List<Comment> roots = rootPage.getRecords();
        if (roots.isEmpty()) {
            return PageUtil.toResponse(rootPage, new ArrayList<>());
        }
        List<Long> rootIds = new ArrayList<>(roots.size());
        for (Comment root : roots) {
            rootIds.add(root.getId());
        }
        List<Comment> replies = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .in(Comment::getParentId, rootIds)
                .eq(Comment::getTargetType, (byte) type)
                .eq(Comment::getTargetId, targetId)
                .eq(Comment::getDeleted, (byte) CommentConstants.NOT_DELETED)
                .orderByAsc(Comment::getCreateTime)
                .orderByAsc(Comment::getId));

        Map<Long, List<CommentVO>> repliesByParent = new HashMap<>(roots.size());
        for (Comment reply : replies) {
            repliesByParent.computeIfAbsent(reply.getParentId(), k -> new ArrayList<>()).add(toVO(reply));
        }
        List<CommentVO> items = new ArrayList<>(roots.size());
        for (Comment root : roots) {
            CommentVO vo = toVO(root);
            vo.setReplies(repliesByParent.getOrDefault(root.getId(), new ArrayList<>()));
            items.add(vo);
        }
        // total 是「一级评论数」，与 interaction_count.comment_count（含二级回复）刻意不等
        return PageUtil.toResponse(rootPage, items);
    }

    private CommentVO toVO(Comment comment) {
        return CommentVO.builder()
                .commentId(comment.getId())
                .userId(comment.getUserId())
                .parentId(comment.getParentId())
                .content(comment.getContent())
                .createTime(comment.getCreateTime())
                .replies(new ArrayList<>())
                .build();
    }

    private int resolveTargetType(Integer targetType) {
        int type = targetType == null ? ContentTargetTypeConstants.ARTICLE : targetType;
        if (!SUPPORTED_TARGET_TYPES.contains(type)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "评论目标类型不合法");
        }
        return type;
    }

    /** 指向二级回复时换成它的一级父评论 id，所有回复在同一根下平铺。 */
    private Long normalizeParentId(Long parentId, int targetType, Long targetId) {
        if (parentId == null) {
            return null;
        }
        Comment parent = commentMapper.selectById(parentId);
        if (parent == null
                || parent.getDeleted() == (byte) CommentConstants.DELETED
                || parent.getTargetType() == null
                || parent.getTargetType() != (byte) targetType
                || !targetId.equals(parent.getTargetId())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "回复的评论不存在");
        }
        return parent.getParentId() == null ? parent.getId() : parent.getParentId();
    }
}
