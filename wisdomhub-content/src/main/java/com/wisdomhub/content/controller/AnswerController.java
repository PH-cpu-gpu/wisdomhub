package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.service.AnswerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "回答controller")
@Validated
@RestController
@RequestMapping("/api/content/answer")
@RequiredArgsConstructor
public class AnswerController {

    private final AnswerService answerService;

    @Operation(summary = "回答点赞（幂等，返回最新点赞数）")
    @PutMapping("/{answerId}/like")
    public R<Integer> like(@PathVariable Long answerId) {
        return R.success(answerService.like(answerId));
    }

    @Operation(summary = "取消回答点赞（幂等，返回最新点赞数）")
    @DeleteMapping("/{answerId}/like")
    public R<Integer> unlike(@PathVariable Long answerId) {
        return R.success(answerService.unlike(answerId));
    }

    @Operation(summary = "删除回答（仅回答者本人）")
    @DeleteMapping("/{answerId}")
    public R<Void> delete(@PathVariable Long answerId) {
        answerService.deleteAnswer(answerId);
        return R.success();
    }
}
