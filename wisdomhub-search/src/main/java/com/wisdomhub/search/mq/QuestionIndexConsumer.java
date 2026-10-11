package com.wisdomhub.search.mq;

import com.wisdomhub.common.mq.constants.MqRoutingKeyConstants;
import com.wisdomhub.common.mq.event.payload.QuestionEventPayload;
import com.wisdomhub.search.config.SearchMqConfiguration;
import com.wisdomhub.search.domain.document.QuestionIndexDoc;
import com.wisdomhub.search.service.EsIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费 question.* 事件维护 question_index。
 * <p>
 * 与文章不同，问题的正文就在 MySQL 的 TEXT 列里（几十到几百字），所以跟着事件一起来，不必回查。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionIndexConsumer {

    private final EsIndexService esIndexService;

    @RabbitListener(queues = SearchMqConfiguration.QUESTION_QUEUE)
    public void onQuestionEvent(QuestionEventEnvelope event) {
        Long questionId = requireQuestionId(event);
        switch (event.getEventType()) {
            case MqRoutingKeyConstants.QUESTION_CREATED -> esIndexService.indexQuestion(toDoc(event.getPayload()));
            case MqRoutingKeyConstants.QUESTION_DELETED -> esIndexService.deleteQuestion(questionId);
            default -> log.debug("忽略不关心的路由键: {}", event.getEventType());
        }
    }

    /** payload 残缺没法处理，抛出去让它重试耗尽后进死信，别静默 ACK 掉 */
    private Long requireQuestionId(QuestionEventEnvelope event) {
        Long questionId = event.getPayload() == null ? null : event.getPayload().getQuestionId();
        if (questionId == null) {
            throw new IllegalArgumentException("questionId 缺失, eventType=" + event.getEventType());
        }
        return questionId;
    }

    private QuestionIndexDoc toDoc(QuestionEventPayload payload) {
        return QuestionIndexDoc.builder()
                .questionId(payload.getQuestionId())
                .title(payload.getTitle())
                .suggest(payload.getTitle())
                .content(payload.getContent())
                .userId(payload.getUserId())
                .answerCount(payload.getAnswerCount())
                .favoriteCount(payload.getFavoriteCount())
                .acceptedAnswerId(payload.getAcceptedAnswerId())
                .createTime(payload.getCreateTime())
                .updateTime(payload.getUpdateTime())
                .build();
    }
}
