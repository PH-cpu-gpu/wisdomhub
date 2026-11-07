package com.wisdomhub.interaction.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 互动计数快照。写入走 InteractionCountMapper 的原子 upsert，这里只用于读。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("interaction_count")
public class InteractionCount implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Byte targetType;

    private Long targetId;

    private Integer likeCount;

    private Integer commentCount;

    private Integer favoriteCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
