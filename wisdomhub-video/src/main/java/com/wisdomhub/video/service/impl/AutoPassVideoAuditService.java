package com.wisdomhub.video.service.impl;

import com.wisdomhub.video.domain.AuditResult;
import com.wisdomhub.video.domain.entity.MediaAsset;
import com.wisdomhub.video.service.VideoAuditService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 框架默认审核实现：转码完成即通过；接数据万象时换 provider=cos 的实现。 */
@Service
@ConditionalOnProperty(prefix = "wisdomhub.video.audit", name = "provider",
        havingValue = "auto", matchIfMissing = true)
public class AutoPassVideoAuditService implements VideoAuditService {

    @Override
    public AuditResult audit(MediaAsset asset) {
        return AuditResult.passed();
    }
}
