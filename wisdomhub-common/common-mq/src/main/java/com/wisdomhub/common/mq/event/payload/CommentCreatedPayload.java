package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 评论发表：targetType 1-文章 2-回答，receiverId 是被评论目标的作者。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreatedPayload implements Serializable {

    private Long commentId;

    private Integer targetType;

    private Long targetId;

    private Long userId;

    private Long receiverId;
}
