package com.wisdomhub.video.service;

import com.wisdomhub.video.domain.dto.UploadTicketDTO;
import com.wisdomhub.video.domain.vo.MediaAssetStatusVO;
import com.wisdomhub.video.domain.vo.UploadTicketVO;

public interface MediaAssetService {

    UploadTicketVO createUploadTicket(UploadTicketDTO dto);

    MediaAssetStatusVO completeUpload(Long assetId);

    MediaAssetStatusVO getAsset(Long assetId);
}
