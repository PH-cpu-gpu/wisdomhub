package com.wisdomhub.content.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.content.domain.dto.CommentCreateDTO;
import com.wisdomhub.content.domain.vo.CommentVO;

public interface CommentService {

    /** 发表评论 / 二级回复，返回新评论 ID。创建类不幂等（17.3） */
    Long createComment(CommentCreateDTO dto);

    /** 删除评论：仅作者本人（403）；一级评论级联软删其二级回复，并按实际软删行数减 comment_count */
    void deleteComment(Long commentId);

    /** 文章评论分页：按一级评论分页，每条内联其二级回复（时间正序） */
    PageResponse<CommentVO> pageComments(Integer targetType, Long targetId, Integer page, Integer pageSize);
}
