package com.wisdomhub.interaction.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.interaction.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "互动收藏controller")
@Validated
@RestController
@RequestMapping("/api/interaction/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏（幂等，返回最新收藏数）")
    @PutMapping
    public R<Integer> favorite(@RequestParam Integer targetType, @RequestParam Long targetId) {
        return R.success(favoriteService.favorite(targetType, targetId));
    }

    @Operation(summary = "取消收藏（幂等，返回最新收藏数）")
    @DeleteMapping
    public R<Integer> unfavorite(@RequestParam Integer targetType, @RequestParam Long targetId) {
        return R.success(favoriteService.unfavorite(targetType, targetId));
    }
}
