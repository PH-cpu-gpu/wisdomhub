package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 草稿箱列表项。不放 summary：草稿期 summary 恒为空，发布时才按最终正文算 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftBriefVO {

    private Long articleId;

    /** 可能为空串，前端显示「无标题」占位 */
    private String title;

    /** 最后编辑时间 */
    private LocalDateTime updateTime;
}
