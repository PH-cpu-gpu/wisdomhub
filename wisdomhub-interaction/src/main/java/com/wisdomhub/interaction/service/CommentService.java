package com.wisdomhub.interaction.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.interaction.domain.dto.CommentCreateDTO;
import com.wisdomhub.interaction.domain.vo.CommentVO;

public interface CommentService {

    Long createComment(CommentCreateDTO dto);

    void deleteComment(Long commentId);

    PageResponse<CommentVO> pageComments(Integer targetType, Long targetId, Integer page, Integer pageSize);
}
