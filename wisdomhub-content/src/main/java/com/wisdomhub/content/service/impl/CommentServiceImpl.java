package com.wisdomhub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.OwnerUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.content.constants.ArticleConstants;
import com.wisdomhub.content.constants.CommentConstants;
import com.wisdomhub.content.domain.dto.CommentCreateDTO;
import com.wisdomhub.content.domain.entity.Article;
import com.wisdomhub.content.domain.entity.Comment;
import com.wisdomhub.content.domain.vo.CommentVO;
import com.wisdomhub.content.mapper.ArticleMapper;
import com.wisdomhub.content.mapper.CommentMapper;
import com.wisdomhub.content.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评论。comment 与 article.comment_count 都在 MySQL，写路径包一个本地事务就原子 ——
 * 提交点即事务提交、补偿即回滚，不需要 B1.18/B1.20 那套跨存储补偿（那套的前提是 Mongo 写无法回滚）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;

    // ==================== 发表 ====================

    @Override
    @Transactional
    public Long createComment(CommentCreateDTO dto) {
        Long userId = CurrentUserUtil.id();
        int targetType = resolveTargetType(dto.getTargetType());
        Long targetId = dto.getTargetId();
        // 写时要校验文章
        assertCommentable(targetId);
        Long parentId = normalizeParentId(dto.getParentId(), targetType, targetId);

        Comment comment = Comment.builder()
                .targetType((byte) targetType)
                .targetId(targetId)
                .userId(userId)
                .parentId(parentId)
                .content(dto.getContent().trim())
                .deleted(CommentConstants.NOT_DELETED.byteValue())
                .build();
        commentMapper.insert(comment);

        int affected = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                // 原子自增，绝不读改写
                .setSql("comment_count = comment_count + 1")
                .eq(Article::getId, targetId)
                .eq(Article::getDeleted, ArticleConstants.NOT_DELETED)
                .eq(Article::getState, ArticleConstants.PUBLISH));
        if (affected == 0) {
            // 上面校验过之后被并发删除/下架：抛出去回滚评论插入，不让评论挂在不可见文章上
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "文章不存在");
        }
        return comment.getId();
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        CurrentUserUtil.id();
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted() == CommentConstants.DELETED.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "评论不存在");
        }
        OwnerUtil.assertOwner(comment.getUserId());

        // 级联：一级评论连它的二级回复一起软删；被删的本身是二级回复时 parent_id 匹配不到行，天然只删自己。
        int affected = commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .set(Comment::getDeleted, CommentConstants.DELETED.byteValue())
                // update(null, wrapper) 不触发自动填充，逻辑删除要自己补 update_time
                .set(Comment::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Comment::getDeleted, CommentConstants.NOT_DELETED)
                .and(w -> w.eq(Comment::getId, commentId).or().eq(Comment::getParentId, commentId)));
        if (affected == 0) {
            // 只有一种可能：selectById 之后被并发删掉了
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "评论不存在");
        }
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .setSql("comment_count = comment_count - " + affected)
                .eq(Article::getId, comment.getTargetId())
                // 非负安全网：历史漂移时不要把计数减成负数
                .ge(Article::getCommentCount, affected));
    }

    // ==================== 分页 ====================

    @Override
    public PageResponse<CommentVO> pageComments(Integer targetType, Long targetId, Integer page, Integer pageSize) {
        int type = resolveTargetType(targetType);
        Page<Comment> rootPage = commentMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getTargetType, type)
                        .eq(Comment::getTargetId, targetId)
                        .isNull(Comment::getParentId)
                        .eq(Comment::getDeleted, CommentConstants.NOT_DELETED)
                        // create_time 是秒精度会重复，用 id 兜底否则翻页会漂移
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
        // 一次 IN 取回当页所有回复再内存分组，避免 N+1。
        List<Comment> replies = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .in(Comment::getParentId, rootIds)
                .eq(Comment::getTargetType, type)
                .eq(Comment::getTargetId, targetId)
                .eq(Comment::getDeleted, CommentConstants.NOT_DELETED)
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
        // total 是「一级评论数」，与 article.comment_count（含二级回复）刻意不等 —— 分页单位 ≠ 计数单位
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
        if (targetType == null) {
            return CommentConstants.TARGET_TYPE_ARTICLE;
        }
        if (!CommentConstants.TARGET_TYPE_ARTICLE.equals(targetType)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "评论目标类型不合法");
        }
        return targetType;
    }

    /** 文章必须存在、未删除、已发布。同库单表查，不是跨服务调用 */
    private void assertCommentable(Long targetId) {
        Article article = articleMapper.selectById(targetId);
        if (article == null
                || article.getDeleted() == ArticleConstants.DELETED.byteValue()
                || article.getState() != ArticleConstants.PUBLISH.byteValue()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "文章不存在");
        }
    }

    /**
     * parentId 归一化：指向二级回复时换成它的一级父评论 id，于是所有回复在同一根下平铺。
     */
    private Long normalizeParentId(Long parentId, int targetType, Long targetId) {
        if (parentId == null) {
            return null;
        }
        Comment parent = commentMapper.selectById(parentId);
        if (parent == null
                || parent.getDeleted() == CommentConstants.DELETED.byteValue()
                || parent.getTargetType() == null
                || parent.getTargetType() != (byte) targetType
                || !targetId.equals(parent.getTargetId())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "回复的评论不存在");
        }
        return parent.getParentId() == null ? parent.getId() : parent.getParentId();
    }
}
