package com.wisdomhub.search.mq;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.UserEventPayload;
import com.wisdomhub.search.config.SearchMqConfiguration;
import com.wisdomhub.search.domain.document.UserIndexDoc;
import com.wisdomhub.search.service.EsIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费 user.* 事件维护 user_index。只有 user.updated 需要处理，其余路由键记 debug 丢掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserIndexConsumer {

    private final EsIndexService esIndexService;

    @RabbitListener(queues = SearchMqConfiguration.USER_QUEUE)
    public void onUserEvent(UserEventEnvelope event) {
        if (!MqRoutingKeyConstants.USER_UPDATED.equals(event.getEventType())) {
            log.debug("忽略不关心的路由键: {}", event.getEventType());
            return;
        }
        UserEventPayload payload = event.getPayload();
        if (payload == null || payload.getUserId() == null) {
            throw new IllegalArgumentException("userId 缺失, eventType=" + event.getEventType());
        }
        esIndexService.indexUser(toDoc(payload));
    }

    private UserIndexDoc toDoc(UserEventPayload payload) {
        return UserIndexDoc.builder()
                .userId(payload.getUserId())
                .username(payload.getUsername())
                .suggest(payload.getUsername())
                .avatar(payload.getAvatar())
                .bio(payload.getBio())
                .createTime(payload.getCreateTime())
                .updateTime(payload.getUpdateTime())
                .build();
    }
}
