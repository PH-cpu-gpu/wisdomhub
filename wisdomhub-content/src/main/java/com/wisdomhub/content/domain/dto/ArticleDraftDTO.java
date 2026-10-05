package com.wisdomhub.content.domain.dto;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 草稿保存请求。新建与更新共用：title / markdown 都可空，但不能同时为空 */
@Data
public class ArticleDraftDTO {

    @Size(max = 100, message = "标题长度不能超过100")
    private String title;

    /** markdown 原文，可空 */
    private String markdown;

    /** 至少一个非空，避免自动保存产出空草稿 */
    @AssertTrue(message = "标题和正文不能同时为空")
    public boolean isContentPresent() {
        return StrUtil.isNotBlank(title) || StrUtil.isNotBlank(markdown);
    }
}
