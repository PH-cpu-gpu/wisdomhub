package com.wisdomhub.recommendation.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.common.security.context.UserContext;
import com.wisdomhub.recommendation.constants.RecommendationConstants;
import com.wisdomhub.recommendation.domain.vo.RecommendationItemVO;
import com.wisdomhub.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "推荐controller")
@Validated
@RestController
@RequestMapping("/api/recommendation")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @Operation(summary = "首页推荐：登录用户按画像召回，游客走热门")
    @GetMapping("/home")
    public R<List<RecommendationItemVO>> home(@RequestParam(required = false) Integer limit) {
        return R.success(recommendationService.home(UserContext.getUserId(),
                RecommendationConstants.clampLimit(limit)));
    }

    @Operation(summary = "相关文章推荐")
    @GetMapping("/related/{articleId}")
    public R<List<RecommendationItemVO>> related(@PathVariable Long articleId,
                                                 @RequestParam(required = false) Integer limit) {
        return R.success(recommendationService.related(articleId, UserContext.getUserId(),
                RecommendationConstants.clampLimit(limit)));
    }

    @Operation(summary = "热门文章")
    @GetMapping("/hot")
    public R<List<RecommendationItemVO>> hot(@RequestParam(required = false) Long tagId,
                                             @RequestParam(required = false) Integer limit) {
        return R.success(recommendationService.hot(tagId,
                RecommendationConstants.clampLimit(limit)));
    }
}
