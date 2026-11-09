package com.wisdomhub.video.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.video.domain.vo.VideoInternalVO;
import com.wisdomhub.video.service.VideoInternalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "服务间视频controller")
@Validated
@RestController
@RequestMapping("/internal/video")
@RequiredArgsConstructor
public class InternalVideoController {

    private final VideoInternalService videoInternalService;

    @Operation(summary = "单条视频完整快照，供 search 建索引；删除态返回 404")
    @GetMapping("/{videoId}")
    public R<VideoInternalVO> getVideo(@NotNull @PathVariable("videoId") Long videoId) {
        return R.success(videoInternalService.getVideo(videoId));
    }

    @Operation(summary = "视频标签 ID 列表，供 interaction 发行为事件")
    @GetMapping("/{videoId}/tag/ids")
    public R<List<Long>> listTagIds(@PathVariable("videoId") Long videoId) {
        return R.success(videoInternalService.tagIdsOf(videoId));
    }

    @Operation(summary = "按 (updateTime, id) 游标增量分页，供 search 对账；since 省略表示从头全表扫")
    @GetMapping("/sync")
    public R<List<VideoInternalVO>> sync(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "0") Long lastId,
            @RequestParam(required = false) Integer size) {
        return R.success(videoInternalService.syncVideos(since, lastId, size));
    }
}
