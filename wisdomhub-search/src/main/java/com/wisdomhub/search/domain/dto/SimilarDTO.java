package com.wisdomhub.search.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 相似文章召回：拿种子文章的内容去 ES 找相近的 */
@Data
public class SimilarDTO {

    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    private List<Long> excludeIds;

    /** 缺省 20，上限 50 */
    private Integer limit;
}
