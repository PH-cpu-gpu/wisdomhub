package com.wisdomhub.notification.session;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdomhub.common.redis.message.SessionKickMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 订阅登出广播，关闭本实例上对应的会话。
 * 广播是即时的尽力而为；丢失时由 {@link SessionSweepScheduler} 按黑名单兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionKickListener implements MessageListener {

    private final StompSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SessionKickMessage kick = objectMapper.readValue(message.getBody(), SessionKickMessage.class);
            if (kick.userId() == null) {
                log.warn("会话踢线消息缺少 userId");
                return;
            }
            int closed;
            if (kick.all()) {
                closed = sessionRegistry.kickByUser(kick.userId(), StompSessionRegistry.TOKEN_REVOKED);
            } else if (StrUtil.isNotBlank(kick.tokenHash())) {
                closed = sessionRegistry.kickByTokenHash(kick.tokenHash(), StompSessionRegistry.TOKEN_REVOKED);
            } else {
                return;
            }
            if (closed > 0) {
                log.info("登出踢线完成, userId={}, all={}, closed={}", kick.userId(), kick.all(), closed);
            }
        } catch (Exception e) {
            log.warn("会话踢线消息处理失败", e);
        }
    }
}