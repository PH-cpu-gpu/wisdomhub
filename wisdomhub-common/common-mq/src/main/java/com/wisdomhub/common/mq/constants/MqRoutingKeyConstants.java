package com.wisdomhub.common.mq.constants;

/**
 * MQ 路由键：统一 {@code <domain>.<action>} 两段式，与事件类名一一对应（ArticleCreatedEvent → article.created）。
 * 固定两段是为了让通配绑定保持简单，{@code *.liked} 即可匹配全部点赞事件。
 * 只放跨服务共识的键；单服务内部自产自销的事件不进 common 层。
 */
public final class MqRoutingKeyConstants {

    public static final String ARTICLE_CREATED = "article.created";
    public static final String ARTICLE_UPDATED = "article.updated";
    public static final String ARTICLE_DELETED = "article.deleted";
    public static final String ARTICLE_LIKED = "article.liked";
    public static final String ARTICLE_COLLECTED = "article.collected";
    public static final String COMMENT_CREATED = "comment.created";

    public static final String QUESTION_CREATED = "question.created";
    public static final String ANSWER_CREATED = "answer.created";

    public static final String USER_REGISTERED = "user.registered";
    public static final String USER_FOLLOWED = "user.followed";

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_PAID = "order.paid";
    public static final String ORDER_REFUNDED = "order.refunded";

    /** Agent 抽取结果，Python 侧产出、knowledge-service 消费 */
    public static final String KNOWLEDGE_EXTRACT = "knowledge.extract";

    /** AI 分析完成 */
    public static final String AI_COMPLETED = "ai.completed";

    private MqRoutingKeyConstants() {
    }
}
