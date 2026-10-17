package com.wisdomhub.notification.service;

import com.wisdomhub.notification.domain.entity.Notification;

public interface NotificationPushService {

    /** 推送给接收者；离线或推送失败都静默，通知行已在 DB 里 */
    void push(Notification notification);
}
