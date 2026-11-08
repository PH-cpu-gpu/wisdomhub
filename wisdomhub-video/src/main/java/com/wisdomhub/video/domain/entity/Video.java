package com.wisdomhub.video.domain.entity;

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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("video")
public class Video implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long assetId;

    private Long authorId;

    private String title;

    private String summary;

    private String coverKey;

    private String playKey;

    private Long durationMs;

    private Integer width;

    private Integer height;

    private Integer viewCount;

    /** 1-已发布 2-已下架 3-已删除 */
    private Integer status;

    private LocalDateTime publishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
