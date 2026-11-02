package com.wisdomhub.content.constants;

public final class TagProposalConstants {

    public static final int TYPE_TAG = 1;
    public static final int TYPE_RELATION = 2;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_EXPIRED = 3;

    public static final int PROPOSER_AI = 1;
    public static final int PROPOSER_ADMIN = 2;
    public static final int PROPOSER_SYSTEM = 3;

    /** 待审提案超过这个天数自动置为过期 */
    public static final int EXPIRE_DAYS = 7;

    private TagProposalConstants() {
    }
}
