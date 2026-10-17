package com.wisdomhub.notification.service;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.notification.domain.entity.Notification;
import com.wisdomhub.notification.domain.vo.NotificationVO;

public interface NotificationService {

    /**
     * 落一条通知；接收者关闭了该类型或自己触发自己时返回 null。
     */
    Notification create(Long userId, Long senderId, int type, int targetType, Long targetId);

    PageResponse<NotificationVO> page(Integer page, Integer pageSize);

    /** 标记单条已读，幂等；不属于当前用户或不存在时 404 */
    void markRead(Long notificationId);

    /** 全部已读，幂等 */
    void markAllRead();

    int getUnreadCount();
}
