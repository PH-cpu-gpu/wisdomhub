package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 回答发表：questionAuthorId 是通知接收者，userId 是回答者。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerCreatedPayload implements Serializable {

    private Long answerId;

    private Long questionId;

    private Long userId;

    private Long questionAuthorId;
}
