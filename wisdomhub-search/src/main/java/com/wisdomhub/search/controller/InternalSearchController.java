package com.wisdomhub.search.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.search.domain.dto.HotDTO;
import com.wisdomhub.search.domain.dto.RecallDTO;
import com.wisdomhub.search.domain.dto.SimilarDTO;
import com.wisdomhub.search.domain.vo.ArticleRecallVO;
import com.wisdomhub.search.service.RecallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 召回出口。
 */
@Tag(name = "召回controller（服务间调用）")
@Validated
@RestController
@RequestMapping("/internal/search")
@RequiredArgsConstructor
public class InternalSearchController {

    private final RecallService recallService;

    @Operation(summary = "按标签 / 关键词召回候选文章")
    @PostMapping("/recall")
    public R<List<ArticleRecallVO>> recall(@Valid @RequestBody RecallDTO dto) {
        return R.success(recallService.recall(dto));
    }

    @Operation(summary = "相似文章召回")
    @PostMapping("/similar")
    public R<List<ArticleRecallVO>> similar(@Valid @RequestBody SimilarDTO dto) {
        return R.success(recallService.similar(dto));
    }

    @Operation(summary = "热门内容召回")
    @PostMapping("/hot")
    public R<List<ArticleRecallVO>> hot(@Valid @RequestBody HotDTO dto) {
        return R.success(recallService.hot(dto));
    }
}
