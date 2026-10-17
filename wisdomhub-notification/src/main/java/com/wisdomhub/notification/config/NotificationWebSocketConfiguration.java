package com.wisdomhub.notification.config;

import com.wisdomhub.notification.interceptor.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.StompBrokerRelayRegistration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * 原生 WebSocket + STOMP，broker 用 RabbitMQ relay（61613）。
 */
@Configuration
@EnableWebSocketMessageBroker
public class NotificationWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final String relayHost;
    private final int relayPort;
    private final String relayVhost;
    private final String relayUser;
    private final String relayPassword;
    private final String allowedOrigins;

    public NotificationWebSocketConfiguration(
            StompAuthChannelInterceptor authInterceptor,
            @Qualifier("notificationWebSocketTaskScheduler") ThreadPoolTaskScheduler notificationWebSocketTaskScheduler,
            @Value("${infra.rabbitmq-host}") String relayHost,
            @Value("${infra.rabbitmq-stomp-port:61613}") int relayPort,
            @Value("${infra.rabbitmq-vhost:/}") String relayVhost,
            @Value("${infra.rabbitmq-username}") String relayUser,
            @Value("${infra.rabbitmq-password}") String relayPassword,
            @Value("${wisdomhub.notification.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.taskScheduler = notificationWebSocketTaskScheduler;
        this.relayHost = relayHost;
        this.relayPort = relayPort;
        this.relayVhost = relayVhost;
        this.relayUser = relayUser;
        this.relayPassword = relayPassword;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/notification/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        StompBrokerRelayRegistration relay = registry.enableStompBrokerRelay("/queue", "/topic")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                .setVirtualHost(relayVhost)
                .setSystemLogin(relayUser)
                .setSystemPasscode(relayPassword)
                .setClientLogin(relayUser)
                .setClientPasscode(relayPassword)
                .setSystemHeartbeatSendInterval(10_000)
                .setSystemHeartbeatReceiveInterval(10_000)
                .setTaskScheduler(taskScheduler);
        // 多实例：本机解析不到用户时广播，由持有该会话的实例继续投递
        relay.setUserDestinationBroadcast("/topic/user-destination.broadcast");
        // 多实例：各实例的用户注册表经 broker 共享
        relay.setUserRegistryBroadcast("/topic/user-registry.broadcast");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(64 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(20_000);
    }
}
