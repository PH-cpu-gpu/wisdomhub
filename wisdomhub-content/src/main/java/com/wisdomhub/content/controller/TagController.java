package com.wisdomhub.content.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.content.constants.TagConstants;
import com.wisdomhub.content.domain.dto.TagDTO;
import com.wisdomhub.content.domain.vo.HotTagVO;
import com.wisdomhub.content.domain.vo.TagVO;
import com.wisdomhub.content.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "标签controller")
@Validated
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @Operation(summary = "全量标签字典")
    @GetMapping("/tag")
    public R<List<TagVO>> list() {
        return R.success(tagService.listAllTags());
    }

    @Operation(summary = "热门标签")
    @GetMapping("/tag/hot")
    public R<List<HotTagVO>> hot(@RequestParam(required = false) Integer limit) {
        // 钳制必须在进缓存方法之前，否则任意整数会撑爆缓存 key 空间
        return R.success(tagService.listHotTags(TagConstants.clampHotLimit(limit)));
    }

    /*
    // 只有管理员能调用, 此处先不实现
    @Operation(summary = "创建标签")
    @PostMapping("/tag")
    public R<Long> create(@Valid @RequestBody TagDTO tagDTO) {
        return R.success(tagService.createTag(tagDTO));
    }
    */

    @Operation(summary = "修改标签")
    @PutMapping("/tag/{tagId}")
    public R<Void> update(@PathVariable Long tagId, @Valid @RequestBody TagDTO tagDTO) {
        tagService.updateTag(tagId, tagDTO);
        return R.success();
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/tag/{tagId}")
    public R<Void> delete(@PathVariable Long tagId) {
        tagService.deleteTag(tagId);
        return R.success();
    }
}
