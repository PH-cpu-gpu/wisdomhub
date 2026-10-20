package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.domain.vo.TagSyncVO;
import com.wisdomhub.content.domain.vo.TagVO;
import com.wisdomhub.content.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "服务间标签controller")
@Validated
@RestController
@RequestMapping("/internal/content")
@RequiredArgsConstructor
public class InternalTagController {

    private final TagService tagService;

    @Operation(summary = "全量标签字典，供 knowledge-service 建图谱种子")
    @GetMapping("/tag/all")
    public R<List<TagVO>> listAllTags() {
        return R.success(tagService.listAllTags());
    }

    @Operation(summary = "按 (updateTime, id) 游标对账分页，供 search 修 tag_index")
    @GetMapping("/tag/sync")
    public R<List<TagSyncVO>> sync(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "0") Long lastId,
            @RequestParam(required = false) Integer size) {
        return R.success(tagService.syncTags(since, lastId, size));
    }
}
