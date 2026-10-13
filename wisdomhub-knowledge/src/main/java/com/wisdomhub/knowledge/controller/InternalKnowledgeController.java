package com.wisdomhub.knowledge.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.knowledge.domain.vo.SeedResultVO;
import com.wisdomhub.knowledge.service.GraphSeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "服务间图谱controller")
@RestController
@RequestMapping("/internal/knowledge")
@RequiredArgsConstructor
public class InternalKnowledgeController {

    private final GraphSeedService graphSeedService;

    @Operation(summary = "图谱种子导入")
    @PostMapping("/seed")
    public R<SeedResultVO> seed() {
        return R.success(graphSeedService.seed());
    }
}
