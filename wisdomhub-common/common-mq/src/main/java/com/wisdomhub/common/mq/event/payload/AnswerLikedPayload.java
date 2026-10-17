package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 回答点赞：authorId 是通知接收者，userId 是点赞者。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerLikedPayload implements Serializable {

    private Long answerId;

    private Long userId;

    private Long authorId;
}
