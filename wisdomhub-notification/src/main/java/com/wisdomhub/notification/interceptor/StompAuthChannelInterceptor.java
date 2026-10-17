package com.wisdomhub.notification.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.wisdomhub.common.redis.constants.RedisSharedConstants;
import com.wisdomhub.common.security.constants.SecurityConstants;
import com.wisdomhub.common.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * STOMP 入口鉴权：握手走网关白名单，JWT 在 CONNECT 帧头里校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.public-key}")
    private String publicKey;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            throw new MessagingException("不支持客户端发送消息");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(SecurityConstants.TOKEN_HEAD_NAME);
        if (StrUtil.isBlank(authorization)) {
            authorization = accessor.getFirstNativeHeader("authorization");
        }
        if (StrUtil.isBlank(authorization)) {
            throw new MessagingException("缺少 Authorization");
        }
        String token = StrUtil.removePrefix(authorization, SecurityConstants.TOKEN_PREFIX);
        Map<String, Object> claims = JwtUtil.parseToken(token, publicKey);
        Object userId = claims.get(SecurityConstants.USER_ID);
        if (userId == null) {
            throw new MessagingException("token 非法或已过期");
        }
        try {
            String blacklistKey = RedisSharedConstants.BLACKLIST_KEY + DigestUtil.sha256Hex(token);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey))) {
                throw new MessagingException("token 已注销");
            }
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            log.warn("STOMP 鉴权黑名单检查失败", e);
            throw new MessagingException("鉴权服务不可用");
        }
        accessor.setUser(() -> String.valueOf(userId));
        // token 不需要转发给 RabbitMQ
        accessor.removeNativeHeader(SecurityConstants.TOKEN_HEAD_NAME);
        accessor.removeNativeHeader("authorization");
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessagingException("未登录");
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/user/")) {
            throw new MessagingException("不允许订阅该目的地: " + destination);
        }
        // 必须与推送 SEND 帧的声明属性一致，否则 RabbitMQ 会报 RESOURCE_LOCKED
        accessor.setNativeHeader("auto-delete", "true");
        accessor.setNativeHeader("durable", "false");
        // STOMP 插件默认把 /queue/ 订阅声明成 exclusive，系统连接就发不进去
        accessor.setNativeHeader("exclusive", "false");
    }
}
