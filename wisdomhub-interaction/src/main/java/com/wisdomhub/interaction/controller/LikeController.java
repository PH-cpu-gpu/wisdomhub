package com.wisdomhub.interaction.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.interaction.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "互动点赞controller")
@Validated
@RestController
@RequestMapping("/api/interaction/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @Operation(summary = "点赞（幂等，返回最新点赞数）")
    @PutMapping
    public R<Integer> like(@RequestParam Integer targetType, @RequestParam Long targetId) {
        return R.success(likeService.like(targetType, targetId));
    }

    @Operation(summary = "取消点赞（幂等，返回最新点赞数）")
    @DeleteMapping
    public R<Integer> unlike(@RequestParam Integer targetType, @RequestParam Long targetId) {
        return R.success(likeService.unlike(targetType, targetId));
    }
}
