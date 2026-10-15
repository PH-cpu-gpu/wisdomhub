package com.wisdomhub.knowledge.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 图谱可视化的一个节点；type 为 TAG / ARTICLE */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphNodeVO {

    private String id;

    private String type;

    /** TAG 用 name，ARTICLE 用标题（向 content-service 补，补不到为 null） */
    private String label;

    /** tag 表的中文分组名，前端着色按它走 */
    private String category;

    /** Tag→Tag 邻居数，前端可按它调节点大小；文章节点没有 */
    private Integer degree;
}
