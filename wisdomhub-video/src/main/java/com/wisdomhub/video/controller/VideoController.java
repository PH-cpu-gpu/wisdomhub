package com.wisdomhub.video.controller;

import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.video.domain.dto.VideoPublishDTO;
import com.wisdomhub.video.domain.dto.VideoTagUpdateDTO;
import com.wisdomhub.video.domain.dto.VideoUpdateDTO;
import com.wisdomhub.video.domain.vo.MyVideoVO;
import com.wisdomhub.video.domain.vo.VideoDetailVO;
import com.wisdomhub.video.domain.vo.VideoListItemVO;
import com.wisdomhub.video.domain.vo.VideoTagVO;
import com.wisdomhub.video.service.VideoPlaybackService;
import com.wisdomhub.video.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag(name = "视频")
@Validated
@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final VideoPlaybackService videoPlaybackService;

    @Operation(summary = "发布视频")
    @PostMapping
    public R<Long> publish(@Valid @RequestBody VideoPublishDTO dto) {
        return R.success(videoService.publish(dto));
    }

    @Operation(summary = "视频详情")
    @GetMapping("/{videoId}")
    public R<VideoDetailVO> detail(@PathVariable("videoId") Long videoId) {
        return R.success(videoService.getDetail(videoId));
    }

    @Operation(summary = "视频列表")
    @GetMapping
    public R<PageResponse<VideoListItemVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Long tagId) {
        return R.success(videoService.list(page, pageSize, authorId, tagId));
    }

    @Operation(summary = "我的视频")
    @GetMapping("/mine")
    public R<PageResponse<MyVideoVO>> mine(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(videoService.mine(page, pageSize));
    }

    @Operation(summary = "编辑视频标题、简介与标签（仅作者）")
    @PutMapping("/{videoId}")
    public R<Void> update(@PathVariable("videoId") Long videoId,
                          @Valid @RequestBody VideoUpdateDTO dto) {
        videoService.update(videoId, dto);
        return R.success();
    }

    @Operation(summary = "删除视频（软删，仅作者）")
    @DeleteMapping("/{videoId}")
    public R<Void> delete(@PathVariable("videoId") Long videoId) {
        videoService.delete(videoId);
        return R.success();
    }

    @Operation(summary = "全量替换视频标签（仅作者）")
    @PutMapping("/{videoId}/tags")
    public R<List<VideoTagVO>> updateTags(@PathVariable("videoId") Long videoId,
                                          @Valid @RequestBody VideoTagUpdateDTO dto) {
        return R.success(videoService.updateTags(videoId, dto.getTagIds()));
    }

    @Operation(summary = "视频播放列表（HLS，分片为签名 URL）")
    @GetMapping(value = "/{videoId}/playlist", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> playlist(@PathVariable("videoId") Long videoId) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                    .cacheControl(CacheControl.noStore())
                    .body(videoPlaybackService.buildPlaylist(videoId));
        } catch (BusinessException e) {
            HttpStatus status = switch (e.getErrorCode().getCode()) {
                case 400 -> HttpStatus.BAD_REQUEST;
                case 401 -> HttpStatus.UNAUTHORIZED;
                case 403 -> HttpStatus.FORBIDDEN;
                case 404 -> HttpStatus.NOT_FOUND;
                case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
            return ResponseEntity.status(status)
                    .contentType(MediaType.TEXT_PLAIN)
                    .cacheControl(CacheControl.noStore())
                    .body(e.getMessage());
        }
    }

    @Operation(summary = "稳定封面入口：302 到签名 URL 或兜底图")
    @GetMapping("/{videoId}/cover")
    public ResponseEntity<Void> cover(@PathVariable("videoId") Long videoId) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(videoPlaybackService.coverUrlById(videoId)))
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @Operation(summary = "兜底封面")
    @GetMapping(value = "/default-cover.svg", produces = "image/svg+xml")
    public ResponseEntity<String> defaultCover() {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720" viewBox="0 0 1280 720">
                  <rect width="1280" height="720" fill="#1f2430"/>
                  <circle cx="640" cy="360" r="96" fill="#2d6cdf"/>
                  <path d="M610 300 L740 360 L610 420 Z" fill="#ffffff"/>
                </svg>
                """;
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body(svg);
    }
}
