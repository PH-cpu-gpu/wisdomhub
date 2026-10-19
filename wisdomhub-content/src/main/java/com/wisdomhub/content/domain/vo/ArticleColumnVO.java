package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 文章所属专栏及其与当前用户的关系，前端据此渲染付费墙 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleColumnVO {

    private Long columnId;

    private String title;

    /** 价格，单位为分，0 表示免费 */
    private Integer price;

    /** 当前用户是否已购买该专栏；游客恒 false */
    private Boolean purchased;

    /** 专栏是否在售（state=1 且未删除），下架专栏不展示购买入口 */
    private Boolean purchasable;
}
