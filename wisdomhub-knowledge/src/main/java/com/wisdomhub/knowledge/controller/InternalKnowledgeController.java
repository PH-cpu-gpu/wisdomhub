package com.wisdomhub.knowledge.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.knowledge.domain.vo.PrerequisiteTreeVO;
import com.wisdomhub.knowledge.domain.vo.SeedResultVO;
import com.wisdomhub.knowledge.domain.vo.TagRelationsVO;
import com.wisdomhub.knowledge.service.GraphQueryService;
import com.wisdomhub.knowledge.service.GraphSeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "服务间图谱controller")
@Validated
@RestController
@RequestMapping("/internal/knowledge")
@RequiredArgsConstructor
public class InternalKnowledgeController {

    private final GraphSeedService graphSeedService;
    private final GraphQueryService graphQueryService;

    @Operation(summary = "图谱种子导入")
    @PostMapping("/seed")
    public R<SeedResultVO> seed() {
        return R.success(graphSeedService.seed());
    }

    @Operation(summary = "按技术名查相关技术")
    @GetMapping("/related")
    public R<TagRelationsVO> relatedByTech(@RequestParam("tech") String tech) {
        return R.success(graphQueryService.relatedByTechName(tech));
    }

    @Operation(summary = "按 tagId 查相关技术")
    @GetMapping("/tag/{tagId}/related")
    public R<TagRelationsVO> relatedByTagId(@NotNull @PathVariable String tagId) {
        return R.success(graphQueryService.related(tagId));
    }

    @Operation(summary = "按技术名查前置知识链")
    @GetMapping("/prerequisites")
    public R<PrerequisiteTreeVO> prerequisitesByTech(@RequestParam("tech") String tech,
                                                     @RequestParam(defaultValue = "5") Integer maxDepth) {
        return R.success(graphQueryService.prerequisitesByTechName(tech, maxDepth));
    }
}
