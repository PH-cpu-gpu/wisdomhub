package com.wisdomhub.user.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.user.domain.vo.LearningRecordVO;
import com.wisdomhub.user.service.LearningRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "学习记录controller")
@Validated
@RestController
@RequestMapping("/api/user/learning")
@RequiredArgsConstructor
public class LearningRecordController {

    private final LearningRecordService learningRecordService;

    @Operation(summary = "加入学习记录")
    @PutMapping("/{technologyName}")
    public R<Void> add(@Size(max = 64, message = "技术名长度不能超过64")
                       @PathVariable String technologyName) {
        learningRecordService.add(technologyName);
        return R.success();
    }

    @Operation(summary = "移除学习记录")
    @DeleteMapping("/{technologyName}")
    public R<Void> remove(@PathVariable String technologyName) {
        learningRecordService.remove(technologyName);
        return R.success();
    }

    @Operation(summary = "标记已掌握")
    @PutMapping("/{technologyName}/mastered")
    public R<Void> markMastered(@PathVariable String technologyName) {
        learningRecordService.markMastered(technologyName);
        return R.success();
    }

    @Operation(summary = "取消已掌握")
    @DeleteMapping("/{technologyName}/mastered")
    public R<Void> unmarkMastered(@PathVariable String technologyName) {
        learningRecordService.unmarkMastered(technologyName);
        return R.success();
    }

    @Operation(summary = "学习记录列表")
    @GetMapping
    public R<PageResponse<LearningRecordVO>> list(@RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(learningRecordService.list(page, pageSize));
    }
}
