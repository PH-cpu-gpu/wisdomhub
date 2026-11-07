package com.wisdomhub.interaction.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.interaction.domain.dto.InteractionCountBatchDTO;
import com.wisdomhub.interaction.domain.vo.InteractionCountBatchVO;
import com.wisdomhub.interaction.domain.vo.InteractionStateVO;
import com.wisdomhub.interaction.service.InteractionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "互动状态与计数controller")
@Validated
@RestController
@RequestMapping("/api/interaction")
@RequiredArgsConstructor
public class InteractionQueryController {

    private final InteractionQueryService interactionQueryService;

    @Operation(summary = "当前用户的互动状态与目标计数")
    @GetMapping("/state")
    public R<InteractionStateVO> state(@RequestParam Integer targetType,
                                       @RequestParam Long targetId) {
        return R.success(interactionQueryService.getState(targetType, targetId));
    }

    @Operation(summary = "批量取互动计数（前端一页一次）")
    @PostMapping("/counts")
    public R<InteractionCountBatchVO> counts(@Valid @RequestBody InteractionCountBatchDTO request) {
        return R.success(interactionQueryService.batchCounts(request));
    }
}
