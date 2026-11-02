package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 提交结果：valid=false 时只有 issues，valid=true 时 proposal 才非空 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagProposalSubmitResult {

    private boolean valid;

    private List<TagProposalIssueVO> issues;

    private TagProposalVO proposal;
}
