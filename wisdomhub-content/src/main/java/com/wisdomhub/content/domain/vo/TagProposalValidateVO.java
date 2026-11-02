package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagProposalValidateVO {

    /** 全部 issue 都是 ERROR，没有 issue 才是 true */
    private boolean valid;

    private Integer proposalType;

    private String targetKey;

    private List<TagProposalIssueVO> issues;
}
