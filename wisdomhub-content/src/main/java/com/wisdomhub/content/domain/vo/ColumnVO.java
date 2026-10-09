package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 专栏。详情与「我的专栏」列表共用这一份：详情没有列表项之外的字段（正文在 article 侧），
 * 不像 ArticleDetailVO 那样需要额外承载正文，没必要拆两个。
 * 只给 userId，创建者昵称头像由前端批量调 user-service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnVO {

    private Long columnId;

    private Long userId;

    private String title;

    private String description;

    private String cover;

    /** 价格，单位为分，0 表示免费 */
    private Integer price;

    private Integer articleCount;

    private LocalDateTime createTime;
}
