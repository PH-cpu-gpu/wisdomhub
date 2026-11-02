package com.wisdomhub.content.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 提案提交；标签类填 name/category/description，关系类填 fromTagId/toTagId/relationType */
@Data
public class TagProposalSubmitDTO {

    @NotNull(message = "提案类型不能为空")
    @Min(value = 1, message = "提案类型不合法")
    @Max(value = 2, message = "提案类型不合法")
    private Integer proposalType;

    @Size(max = 50, message = "标签名长度不能超过50")
    private String name;

    @Size(max = 20, message = "分组长度不能超过20")
    private String category;

    @Size(max = 255, message = "描述长度不能超过255")
    private String description;

    private Long fromTagId;

    private Long toTagId;

    @Size(max = 20, message = "关系类型长度不能超过20")
    private String relationType;

    @Min(value = 1, message = "提案人类型不合法")
    @Max(value = 3, message = "提案人类型不合法")
    private Integer proposerType;

    private Long proposerId;

    private JsonNode evidence;
}
