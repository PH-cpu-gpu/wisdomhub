package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.dto.UnknownEntityHitDTO;
import com.wisdomhub.content.service.UnknownEntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "服务间未收录实体controller")
@Validated
@RestController
@RequestMapping("/internal/content/unknown-entity")
@RequiredArgsConstructor
public class InternalUnknownEntityController {

    private final UnknownEntityService unknownEntityService;

    @Operation(summary = "上报文章抽取丢弃的未收录实体，返回新增条数")
    @PostMapping("/hits")
    public R<Integer> recordHits(@Valid @RequestBody UnknownEntityHitDTO dto) {
        return R.success(unknownEntityService.recordHits(dto));
    }
}
