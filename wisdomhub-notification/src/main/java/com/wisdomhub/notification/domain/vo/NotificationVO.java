package com.wisdomhub.notification.domain.vo;

import com.wisdomhub.notification.domain.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通知列表项。文案由前端按 type 拼，昵称头像批量调 user-service，文章标题批量调 content brief。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVO {

    private Long notificationId;

    private Integer type;

    private Integer targetType;

    private Long targetId;

    private Long senderId;

    private String content;

    private Boolean read;

    private LocalDateTime createTime;

    public static NotificationVO from(Notification notification) {
        return NotificationVO.builder()
                .notificationId(notification.getId())
                .type(notification.getType() == null ? null : notification.getType().intValue())
                .targetType(notification.getTargetType() == null ? null : notification.getTargetType().intValue())
                .targetId(notification.getTargetId())
                .senderId(notification.getSenderId())
                .content(notification.getContent())
                .read(notification.getIsRead() != null && notification.getIsRead() != 0)
                .createTime(notification.getCreateTime())
                .build();
    }
}
