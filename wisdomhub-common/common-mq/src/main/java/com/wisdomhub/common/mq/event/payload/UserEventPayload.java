package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户资料变更快照。只放对外展示的字段 —— email / phone / password 不进 MQ，
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEventPayload implements Serializable {

    private Long userId;

    private String username;

    private String avatar;

    private String bio;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
