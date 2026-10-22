package com.wisdomhub.search.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应 user-service 的 UserSyncVO（本服务只消费，不反向定义契约）。
 */
@Data
public class UserSyncDTO {

    private Long userId;

    private String username;

    private String avatar;

    private String bio;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
