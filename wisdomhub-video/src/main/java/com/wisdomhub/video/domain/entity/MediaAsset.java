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
@TableName("media_asset")
public class MediaAsset implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long ownerId;

    private String objectKey;

    /** 申请时为声明值，校验后回写实际值 */
    private Long fileSize;

    private String mimeType;

    private Long durationMs;

    private Integer width;

    private Integer height;

    private String coverKey;

    /** 1-待上传 2-已上传 3-转码中 4-失败 5-审核中 6-审核拒绝 7-就绪 */
    private Integer status;

    private String failReason;

    private String mpsTaskId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
