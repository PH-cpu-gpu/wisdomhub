package com.wisdomhub.video.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.video.domain.dto.UploadTicketDTO;
import com.wisdomhub.video.domain.vo.MediaAssetStatusVO;
import com.wisdomhub.video.domain.vo.UploadTicketVO;
import com.wisdomhub.video.service.MediaAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "视频上传")
@Validated
@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class MediaAssetController {

    private final MediaAssetService mediaAssetService;

    @Operation(summary = "申请上传凭证")
    @PostMapping("/upload-ticket")
    public R<UploadTicketVO> uploadTicket(@Valid @RequestBody UploadTicketDTO dto) {
        return R.success(mediaAssetService.createUploadTicket(dto));
    }

    @Operation(summary = "直传完成确认")
    @PostMapping("/assets/{assetId}/complete")
    public R<MediaAssetStatusVO> complete(@PathVariable("assetId") Long assetId) {
        return R.success(mediaAssetService.completeUpload(assetId));
    }

    @Operation(summary = "查询资产状态（仅所有者）")
    @GetMapping("/assets/{assetId}")
    public R<MediaAssetStatusVO> getAsset(@PathVariable("assetId") Long assetId) {
        return R.success(mediaAssetService.getAsset(assetId));
    }
}
