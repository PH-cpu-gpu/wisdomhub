package com.wisdomhub.content.util;

import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.content.constants.TagProposalIssueCodes;

/** issue code → HTTP 语义码；只用于把提交结果包成 R。 */
public final class TagProposalErrorMapper {

    private TagProposalErrorMapper() {
    }

    public static CommonErrorCode toErrorCode(String issueCode) {
        return switch (issueCode) {
            case TagProposalIssueCodes.PENDING_EXISTS,
                 TagProposalIssueCodes.TAG_EXISTS,
                 TagProposalIssueCodes.RELATION_EXISTS,
                 TagProposalIssueCodes.RELATION_CYCLE -> CommonErrorCode.CONFLICT;
            case TagProposalIssueCodes.TAG_NOT_FOUND -> CommonErrorCode.NOT_FOUND;
            default -> CommonErrorCode.BAD_REQUEST;
        };
    }
}
