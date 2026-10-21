package com.wisdomhub.user.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.wisdomhub.common.mq.event.ArticleDeletedEvent;
import com.wisdomhub.user.config.UserMqConfiguration;
import com.wisdomhub.user.domain.entity.UserBrowseHistory;
import com.wisdomhub.user.mapper.UserBrowseHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 文章删除后按 article_id 物理删浏览历史；按主键删天然幂等，不需要 eventId 去重。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleDeletedConsumer {

    private final UserBrowseHistoryMapper userBrowseHistoryMapper;

    @RabbitListener(queues = UserMqConfiguration.BROWSE_HISTORY_QUEUE)
    public void onArticleDeleted(ArticleDeletedEvent event, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long articleId = event.getPayload() == null ? null : event.getPayload().getArticleId();
        if (articleId == null) {
            throw new IllegalArgumentException("articleId 缺失, eventId=" + event.getEventId());
        }
        int deleted = userBrowseHistoryMapper.delete(new LambdaQueryWrapper<UserBrowseHistory>()
                .eq(UserBrowseHistory::getArticleId, articleId));
        log.info("已清浏览历史: articleId={}, rows={}", articleId, deleted);
        channel.basicAck(deliveryTag, false);
    }
}
