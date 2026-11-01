package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.common.web.annotation.RequireAdmin;
import com.wisdomhub.content.domain.dto.TagAliasDTO;
import com.wisdomhub.content.domain.vo.TagAliasVO;
import com.wisdomhub.content.service.TagAliasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "标签别名controller")
@Validated
@RestController
@RequestMapping("/api/content/tag/{tagId}/alias")
@RequiredArgsConstructor
@RequireAdmin
public class TagAliasController {

    private final TagAliasService tagAliasService;

    @Operation(summary = "标签别名列表")
    @GetMapping
    public R<List<TagAliasVO>> list(@PathVariable Long tagId) {
        return R.success(tagAliasService.list(tagId));
    }

    @Operation(summary = "新增标签别名")
    @PostMapping
    public R<TagAliasVO> add(@PathVariable Long tagId, @Valid @RequestBody TagAliasDTO dto) {
        return R.success(tagAliasService.add(tagId, dto));
    }

    @Operation(summary = "删除标签别名")
    @DeleteMapping("/{aliasId}")
    public R<Void> delete(@PathVariable Long tagId, @PathVariable Long aliasId) {
        tagAliasService.delete(tagId, aliasId);
        return R.success();
    }
}
