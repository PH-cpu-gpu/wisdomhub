package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.common.feign.dto.TargetInfoDTO;
import com.wisdomhub.content.service.TargetProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "服务间目标校验controller")
@Validated
@RestController
@RequestMapping("/internal/targets")
@RequiredArgsConstructor
public class InternalTargetController {

    private final TargetProviderService targetProviderService;

    @Operation(summary = "校验互动目标是否存在、可见，并返回作者 ID")
    @GetMapping("/{type}/{id}")
    public R<TargetInfoDTO> getTarget(@PathVariable("type") String type,
                                      @PathVariable("id") Long id) {
        return R.success(targetProviderService.getTarget(type, id));
    }
}
