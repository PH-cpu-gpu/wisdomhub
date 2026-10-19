package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 文章付费墙判定结果：locked=true 时正文不返回，仅保留可见的专栏购买入口 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleAccessVO {

    private Boolean locked;

    private List<ArticleColumnVO> columns;
}
