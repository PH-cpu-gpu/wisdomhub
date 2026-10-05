package com.wisdomhub.content.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ArticlePublishDTO {

    /**
     * 不带则新分配雪花 ID。带了就必须是本人写的文章，否则 403
     */
    private Long articleId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100")
    private String title;

    /**
     * 可选。留空则由正文纯文本截取
     */
    @Size(max = 255, message = "摘要长度不能超过255")
    private String summary;

    @NotBlank(message = "正文不能为空")
    private String markdown;

    /**
     * 标签 ID 列表，可空。null = 覆盖发布时保持原关联不变；[] = 清空；非空 = 全量替换。
     * 元素必须是已存在的标签，不允许随文章创建新标签
     */
    @Size(max = 10, message = "一篇文章最多选择10个标签")
    private List<Long> tagIds;
}
