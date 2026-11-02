package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.common.web.annotation.RequireAdmin;
import com.wisdomhub.content.domain.vo.UnknownEntityVO;
import com.wisdomhub.content.service.UnknownEntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "未收录实体controller")
@Validated
@RestController
@RequestMapping("/api/content/unknown-entity")
@RequiredArgsConstructor
@RequireAdmin
public class UnknownEntityController {

    private final UnknownEntityService unknownEntityService;

    @Operation(summary = "未收录实体候选分页；extractCount 是不同文章数")
    @GetMapping
    public R<PageResponse<UnknownEntityVO>> page(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Boolean promoted,
                                                 @RequestParam(required = false) Integer minExtractCount,
                                                 @RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(unknownEntityService.page(keyword, promoted, minExtractCount, page, pageSize));
    }
}
