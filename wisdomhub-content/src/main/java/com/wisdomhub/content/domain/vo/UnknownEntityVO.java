package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 未收录实体候选，供管理员判断要不要手工建标签 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnknownEntityVO {

    private Long id;

    private String name;

    private String nameNorm;

    /** 命中的不同文章数，升级门槛看这个 */
    private Integer extractCount;

    /** 历史查询次数，已停止采集 */
    private Integer queryCount;

    private Long sampleArticleId;

    /** 已升级成的提案 ID，为空表示还没提过 */
    private Long promotedProposalId;

    private LocalDateTime firstSeen;

    private LocalDateTime lastSeen;
}
