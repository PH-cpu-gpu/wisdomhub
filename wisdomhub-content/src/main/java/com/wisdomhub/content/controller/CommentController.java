package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.dto.CommentCreateDTO;
import com.wisdomhub.content.domain.vo.CommentVO;
import com.wisdomhub.content.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "评论controller")
@Validated
@RestController
@RequestMapping("/api/content/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "发表评论 / 二级回复（回复二级评论传 parentId，服务端归一到一级父评论）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody CommentCreateDTO commentCreateDTO) {
        return R.success(commentService.createComment(commentCreateDTO));
    }

    @Operation(summary = "评论分页（按一级评论分页，附二级回复）")
    @GetMapping
    public R<PageResponse<CommentVO>> page(@RequestParam(required = false) Integer targetType,
                                           @RequestParam Long targetId,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(commentService.pageComments(targetType, targetId, page, pageSize));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{commentId}")
    public R<Void> delete(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return R.success();
    }
}
