package com.wisdomhub.interaction.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.interaction.domain.dto.InteractionCountBatchDTO;
import com.wisdomhub.interaction.domain.vo.InteractionCountBatchVO;
import com.wisdomhub.interaction.service.InteractionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "服务间互动计数controller")
@Validated
@RestController
@RequestMapping("/internal/interaction")
@RequiredArgsConstructor
public class InternalInteractionController {

    private final InteractionQueryService interactionQueryService;

    @Operation(summary = "批量取互动计数（search/recommendation 用）")
    @PostMapping("/counts")
    public R<InteractionCountBatchVO> counts(@Valid @RequestBody InteractionCountBatchDTO request) {
        return R.success(interactionQueryService.batchCounts(request));
    }
}
