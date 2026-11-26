package com.wisdomhub.notification.config;

import com.wisdomhub.common.redis.constants.RedisSharedConstants;
import com.wisdomhub.notification.session.SessionKickListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 登出踢线的 Redis Pub/Sub 订阅 */
@Configuration
public class NotificationSessionRedisConfiguration {

    @Bean
    public RedisMessageListenerContainer sessionKickListenerContainer(RedisConnectionFactory connectionFactory,
                                                                     SessionKickListener sessionKickListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(sessionKickListener, new ChannelTopic(RedisSharedConstants.SESSION_KICK_CHANNEL));
        return container;
    }
}