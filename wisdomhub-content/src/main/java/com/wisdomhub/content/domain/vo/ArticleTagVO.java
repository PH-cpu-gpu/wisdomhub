package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章标签的精简形态，只给内部同步链路用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleTagVO {

    private Long tagId;

    private String name;

    /** 展示分组，对应 TagVO.category */
    private String category;
}
