package com.wisdomhub.content.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ColumnCreateDTO {

    @NotBlank(message = "专栏名不能为空")
    @Size(max = 100, message = "专栏名长度不能超过100")
    private String title;

    @Size(max = 500, message = "简介长度不能超过500")
    private String description;

    @Size(max = 512, message = "封面地址长度不能超过512")
    private String cover;

    /** 价格，单位为分，0 表示免费。可空，缺省 0。购买流程归 B2.23~B2.25，这里只存不判 */
    @Min(value = 0, message = "价格不能为负")
    private Integer price;
}
