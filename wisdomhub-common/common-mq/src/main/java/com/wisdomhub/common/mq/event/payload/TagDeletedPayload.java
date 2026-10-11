package com.wisdomhub.common.mq.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 标签是**物理删除**（tag 表没有 deleted 列），所以删除事件是下游唯一能拿到的线索。
 * 它同时要触发 article_index 里残留标签的清理 —— 那些文章的行没动、发不出 article.updated。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagDeletedPayload implements Serializable {

    private Long tagId;
}
