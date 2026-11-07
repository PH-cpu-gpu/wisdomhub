package com.wisdomhub.interaction.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 点赞关系。物理删除，唯一键保证幂等；表只有 create_time，不要多出 update_time。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("content_like")
public class ContentLike implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 1-文章 2-回答 3-问题 4-专栏 5-用户 6-标签提案 7-视频 */
    private Byte targetType;

    private Long targetId;

    /** 点赞用户 ID */
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
