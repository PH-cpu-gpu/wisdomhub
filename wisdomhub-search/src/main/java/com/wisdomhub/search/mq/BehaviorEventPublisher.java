package com.wisdomhub.search.mq;

import cn.hutool.core.util.StrUtil;
import com.wisdomhub.common.mq.constants.BehaviorTypeConstants;
import com.wisdomhub.common.mq.event.BehaviorRecordedEvent;
import com.wisdomhub.common.mq.event.payload.BehaviorRecordedPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 登录用户的搜索行为，游客不采集；失败只记日志，不影响搜索 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorEventPublisher {

    private final EventPublisher eventPublisher;

    public void search(String keyword, Long tagId) {
        Long userId = UserContext.getUserId();
        if (userId == null || (StrUtil.isBlank(keyword) && tagId == null)) {
            return;
        }
        try {
            eventPublisher.send(new BehaviorRecordedEvent(BehaviorRecordedPayload.builder()
                    .userId(userId).behaviorType(BehaviorTypeConstants.SEARCH)
                    .keyword(StrUtil.trim(keyword)).tagId(tagId).build()));
        } catch (Exception e) {
            log.warn("搜索行为事件发送失败, userId={}, tagId={}", userId, tagId, e);
        }
    }
}
