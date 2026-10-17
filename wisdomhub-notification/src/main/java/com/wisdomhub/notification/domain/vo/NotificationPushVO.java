package com.wisdomhub.notification.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 推送载荷：新通知 + 推送后的未读数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPushVO {

    private NotificationVO notification;

    private Integer unreadCount;
}
