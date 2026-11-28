package com.wisdomhub.recommendation.service;

import com.wisdomhub.common.mq.event.payload.BehaviorRecordedPayload;
import com.wisdomhub.recommendation.domain.vo.InterestProfileVO;

public interface InterestProfileService {

    /** 记录一次行为，按标签加权；同一 eventId 只生效一次 */
    boolean record(String eventId, BehaviorRecordedPayload payload);

    /** 最近 3 个月合并后的标签权重，按分数倒序 */
    InterestProfileVO getProfile(Long userId);
}
