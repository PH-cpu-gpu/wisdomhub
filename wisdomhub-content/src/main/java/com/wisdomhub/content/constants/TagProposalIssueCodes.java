package com.wisdomhub.content.constants;

/** dry-run 返回的 issue code；Python 侧按这些值决定是否提交。 */
public final class TagProposalIssueCodes {

    public static final String SEVERITY_ERROR = "ERROR";

    public static final String PROPOSAL_TYPE_INVALID = "PROPOSAL_TYPE_INVALID";
    public static final String NAME_REQUIRED = "NAME_REQUIRED";
    public static final String NAME_TOO_LONG = "NAME_TOO_LONG";
    public static final String CATEGORY_TOO_LONG = "CATEGORY_TOO_LONG";
    public static final String DESCRIPTION_TOO_LONG = "DESCRIPTION_TOO_LONG";
    public static final String TAG_EXISTS = "TAG_EXISTS";
    public static final String PENDING_EXISTS = "PENDING_EXISTS";

    public static final String RELATION_ENDPOINT_REQUIRED = "RELATION_ENDPOINT_REQUIRED";
    public static final String RELATION_SELF_LOOP = "RELATION_SELF_LOOP";
    public static final String RELATION_TYPE_INVALID = "RELATION_TYPE_INVALID";
    public static final String TAG_NOT_FOUND = "TAG_NOT_FOUND";
    public static final String RELATION_EXISTS = "RELATION_EXISTS";
    public static final String RELATION_CYCLE = "RELATION_CYCLE";

    public static final String PROPOSER_TYPE_INVALID = "PROPOSER_TYPE_INVALID";

    private TagProposalIssueCodes() {
    }
}
