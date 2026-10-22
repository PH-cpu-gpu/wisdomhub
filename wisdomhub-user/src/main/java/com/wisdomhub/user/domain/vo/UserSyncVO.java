package com.wisdomhub.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 服务间读契约：user_index 对账用的用户快照。email / phone / password 不出现。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSyncVO {

    private Long userId;

    private String username;

    private String avatar;

    private String bio;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
