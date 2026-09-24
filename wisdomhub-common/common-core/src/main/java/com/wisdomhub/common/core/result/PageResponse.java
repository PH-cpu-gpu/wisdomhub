package com.wisdomhub.common.core.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageResponse<T> {

    @Schema(description = "数据总条数")
    private Integer total;

    @Schema(description = "总页数")
    private Integer pages;

    @Schema(description = "页尺寸")
    private Integer pageSize;

    @Schema(description = "页码")
    private Integer page;

    @Schema(description = "数据列表")
    private List<T> items;

}
