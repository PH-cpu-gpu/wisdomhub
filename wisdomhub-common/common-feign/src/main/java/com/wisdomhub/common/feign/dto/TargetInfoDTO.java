package com.wisdomhub.common.feign.dto;

import lombok.Data;

/** 互动目标校验结果。 */
@Data
public class TargetInfoDTO {

    /** 目标是否存在（未删除） */
    private Boolean exists;

    /** 是否公开可见；exists=false 时恒为 false */
    private Boolean visible;

    /** 目标作者；无主目标为 null */
    private Long ownerId;
}
