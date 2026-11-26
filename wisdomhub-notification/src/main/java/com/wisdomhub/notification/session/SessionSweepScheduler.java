package com.wisdomhub.notification.session;

import com.wisdomhub.common.redis.constants.RedisSharedConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 会话状态在实例内存里，必须每实例各自扫描：
 * 关掉到期的 token；并复查黑名单，兜住登出广播丢失或实例重启。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionSweepScheduler {

    private static final long INITIAL_DELAY_SECONDS = 30;

    private final StompSessionRegistry sessionRegistry;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${wisdomhub.notification.session.enabled:true}")
    private boolean enabled;

    @Value("${wisdomhub.notification.session.sweep-interval-seconds:60}")
    private long sweepIntervalSeconds;

    private ThreadPoolTaskScheduler scheduler;

    @PostConstruct
    void start() {
        if (!enabled) {
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-session-sweep-");
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::sweep,
                Instant.now().plusSeconds(INITIAL_DELAY_SECONDS),
                Duration.ofSeconds(sweepIntervalSeconds));
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    void sweep() {
        try {
            int expired = sessionRegistry.kickExpired(System.currentTimeMillis());
            if (expired > 0) {
                log.info("STOMP 会话到期关闭, count={}", expired);
            }
            int revoked = kickBlacklisted();
            if (revoked > 0) {
                log.info("STOMP 会话黑名单兜底关闭, count={}", revoked);
            }
        } catch (Exception e) {
            log.warn("STOMP 会话扫描失败", e);
        }
    }

    private int kickBlacklisted() {
        Set<String> tokenHashes = sessionRegistry.snapshotTokenHashes();
        if (tokenHashes.isEmpty()) {
            return 0;
        }
        List<String> hashes = new ArrayList<>(tokenHashes);
        List<String> keys = hashes.stream()
                .map(hash -> RedisSharedConstants.BLACKLIST_KEY + hash)
                .toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return 0;
        }
        Set<String> revoked = new HashSet<>();
        for (int i = 0; i < hashes.size(); i++) {
            if (values.get(i) != null) {
                revoked.add(hashes.get(i));
            }
        }
        return sessionRegistry.kickByTokenHashes(revoked, StompSessionRegistry.TOKEN_REVOKED);
    }
}