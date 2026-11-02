package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.common.web.annotation.RequireAdmin;
import com.wisdomhub.content.domain.dto.TagRelationDTO;
import com.wisdomhub.content.domain.vo.TagRelationVO;
import com.wisdomhub.content.service.TagRelationService;
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

@Tag(name = "标签关系controller")
@Validated
@RestController
@RequestMapping("/api/content/tag")
@RequiredArgsConstructor
@RequireAdmin
public class TagRelationController {

    private final TagRelationService tagRelationService;

    @Operation(summary = "标签的关系列表")
    @GetMapping("/{tagId}/relation")
    public R<List<TagRelationVO>> list(@PathVariable Long tagId) {
        return R.success(tagRelationService.listByTag(tagId));
    }

    @Operation(summary = "新增标签关系")
    @PostMapping("/relation")
    public R<TagRelationVO> create(@Valid @RequestBody TagRelationDTO dto) {
        return R.success(tagRelationService.create(dto));
    }

    @Operation(summary = "删除标签关系")
    @DeleteMapping("/relation/{relationId}")
    public R<Void> delete(@PathVariable Long relationId) {
        tagRelationService.delete(relationId);
        return R.success();
    }
}
