package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 标签名解析结果：name 是调用方传入的原名，tagId 是命中的标签 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagResolveVO {

    private String name;

    private Long tagId;

    private String canonicalName;
}
