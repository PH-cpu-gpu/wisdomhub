package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 删除事件只带身份，不带正文*/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDeletedPayload implements Serializable {

    private Long questionId;

    private Long userId;
}
