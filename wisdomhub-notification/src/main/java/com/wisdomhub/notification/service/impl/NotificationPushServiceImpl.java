package com.wisdomhub.notification.service.impl;

import com.wisdomhub.notification.domain.entity.Notification;
import com.wisdomhub.notification.domain.vo.NotificationPushVO;
import com.wisdomhub.notification.domain.vo.NotificationVO;
import com.wisdomhub.notification.service.NotificationPushService;
import com.wisdomhub.notification.service.UnreadCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushServiceImpl implements NotificationPushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final UnreadCountService unreadCountService;

    @Override
    public void push(Notification notification) {
        try {
            NotificationPushVO payload = NotificationPushVO.builder()
                    .notification(NotificationVO.from(notification))
                    .unreadCount(unreadCountService.get(notification.getUserId()))
                    .build();
            // 与订阅帧的队列声明属性保持一致，且断开后自动删除会话队列
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(notification.getUserId()),
                    "/queue/notification",
                    payload,
                    Map.of("auto-delete", "true", "durable", "false", "exclusive", "false"));
        } catch (Exception e) {
            // 推送失败绝不能抛给 MQ 消费者：消费者会删去重 key 重试，导致重复落库
            log.warn("通知实时推送失败, notificationId={}, userId={}",
                    notification.getId(), notification.getUserId(), e);
        }
    }
}
