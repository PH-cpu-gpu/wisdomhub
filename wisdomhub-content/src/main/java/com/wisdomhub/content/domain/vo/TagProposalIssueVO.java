package com.wisdomhub.content.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagProposalIssueVO {

    /** 出问题的字段名 */
    private String field;

    /** 机器可读的 issue code */
    private String code;

    private String message;

    private String severity;
}
