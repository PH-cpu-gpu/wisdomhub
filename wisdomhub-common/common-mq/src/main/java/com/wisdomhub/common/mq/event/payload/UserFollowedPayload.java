package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 关注：followeeId 是通知接收者。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFollowedPayload implements Serializable {

    private Long followerId;

    private Long followeeId;
}
