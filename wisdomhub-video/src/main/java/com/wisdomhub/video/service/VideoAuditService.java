package com.wisdomhub.video.service;

import com.wisdomhub.video.domain.AuditResult;
import com.wisdomhub.video.domain.entity.MediaAsset;

/** 转码完成后的审核扩展点；默认实现自动通过。 */
public interface VideoAuditService {

    AuditResult audit(MediaAsset asset);
}
