package com.wisdomhub.content.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagProposalVO {

    private Long id;

    private Integer proposalType;

    private Integer status;

    private String targetKey;

    private String name;

    private String category;

    private String description;

    private Long fromTagId;

    private Long toTagId;

    private String relationType;

    private Integer proposerType;

    private Long proposerId;

    private Long decidedBy;

    private LocalDateTime decidedAt;

    private LocalDateTime createTime;

    private JsonNode evidence;
}
