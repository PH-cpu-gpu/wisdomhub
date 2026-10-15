package com.wisdomhub.knowledge.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.knowledge.domain.vo.PrerequisiteTreeVO;
import com.wisdomhub.knowledge.domain.vo.TagDetailVO;
import com.wisdomhub.knowledge.domain.vo.TagRelationsVO;
import com.wisdomhub.knowledge.service.GraphQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "图谱查询controller")
@Validated
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final GraphQueryService graphQueryService;

    @Operation(summary = "技术详情（节点属性 + 相关技术）")
    @GetMapping("/tag/{tagId}")
    public R<TagDetailVO> detail(@NotNull @PathVariable String tagId) {
        return R.success(graphQueryService.detail(tagId));
    }

    @Operation(summary = "相关技术（全部直接邻居，按关系类型分组）")
    @GetMapping("/tag/{tagId}/related")
    public R<TagRelationsVO> related(@NotNull @PathVariable String tagId) {
        return R.success(graphQueryService.related(tagId));
    }

    @Operation(summary = "前置知识链（沿 PREREQUISITE 按层展开）")
    @GetMapping("/tag/{tagId}/prerequisites")
    public R<PrerequisiteTreeVO> prerequisites(@NotNull @PathVariable String tagId,
                                               @RequestParam(defaultValue = "5") Integer maxDepth) {
        return R.success(graphQueryService.prerequisites(tagId, maxDepth));
    }
}
