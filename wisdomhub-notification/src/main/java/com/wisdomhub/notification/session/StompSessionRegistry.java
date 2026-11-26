package com.wisdomhub.notification.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本实例持有的 STOMP 会话索引：sessionId 关联用户、token 摘要与到期时间。
 * 只覆盖本实例的连接，跨实例由 Redis 频道广播后各实例各自关闭。
 */
@Slf4j
@Component
public class StompSessionRegistry {

    /** 建连时的 accessToken 已到期，客户端应刷新后重连 */
    public static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4001, "token expired");

    /** accessToken 已被拉黑（登出），客户端不应再重连 */
    public static final CloseStatus TOKEN_REVOKED = new CloseStatus(4002, "token revoked");

    private final Map<String, SessionMeta> sessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> transports = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionsByToken = new ConcurrentHashMap<>();

    /** 传输层建连时登记 WebSocketSession；早于 STOMP CONNECT 帧 */
    public void attach(WebSocketSession session) {
        transports.put(session.getId(), session);
    }

    /** STOMP CONNECT 鉴权通过后登记业务元数据 */
    public void register(String sessionId, Long userId, String tokenHash, long expireAtMillis) {
        SessionMeta meta = new SessionMeta(sessionId, userId, tokenHash, expireAtMillis);
        SessionMeta previous = sessions.put(sessionId, meta);
        if (previous != null) {
            removeFromIndexes(previous);
        }
        sessionsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionsByToken.computeIfAbsent(tokenHash, key -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    /** 连接关闭时清理（含 WebSocketSession 引用） */
    public void detach(String sessionId) {
        transports.remove(sessionId);
        SessionMeta meta = sessions.remove(sessionId);
        if (meta != null) {
            removeFromIndexes(meta);
        }
    }

    public int kickByTokenHash(String tokenHash, CloseStatus status) {
        Set<String> sessionIds = sessionsByToken.get(tokenHash);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }
        return kick(new ArrayList<>(sessionIds), status);
    }

    public int kickByUser(Long userId, CloseStatus status) {
        Set<String> sessionIds = sessionsByUser.get(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }
        return kick(new ArrayList<>(sessionIds), status);
    }

    public int kickByTokenHashes(Set<String> tokenHashes, CloseStatus status) {
        int closed = 0;
        for (String tokenHash : tokenHashes) {
            closed += kickByTokenHash(tokenHash, status);
        }
        return closed;
    }

    public int kickExpired(long nowMillis) {
        List<String> expired = sessions.values().stream()
                .filter(meta -> meta.expireAtMillis() <= nowMillis)
                .map(SessionMeta::sessionId)
                .toList();
        return kick(expired, TOKEN_EXPIRED);
    }

    public Set<String> snapshotTokenHashes() {
        return Set.copyOf(sessionsByToken.keySet());
    }

    private int kick(List<String> sessionIds, CloseStatus status) {
        int closed = 0;
        for (String sessionId : sessionIds) {
            // 摘元数据的动作原子化：广播与扫描并发时只有一个线程真正 close
            SessionMeta meta = sessions.remove(sessionId);
            if (meta == null) {
                continue;
            }
            removeFromIndexes(meta);
            WebSocketSession session = transports.remove(sessionId);
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.close(status);
                closed++;
            } catch (IOException e) {
                log.warn("关闭 STOMP 会话失败, sessionId={}", sessionId, e);
            }
        }
        return closed;
    }

    private void removeFromIndexes(SessionMeta meta) {
        removeFromIndex(sessionsByUser, meta.userId(), meta.sessionId());
        removeFromIndex(sessionsByToken, meta.tokenHash(), meta.sessionId());
    }

    private <K> void removeFromIndex(Map<K, Set<String>> index, K key, String sessionId) {
        Set<String> sessionIds = index.get(key);
        if (sessionIds == null) {
            return;
        }
        sessionIds.remove(sessionId);
        if (sessionIds.isEmpty()) {
            index.remove(key, sessionIds);
        }
    }

    private record SessionMeta(String sessionId, Long userId, String tokenHash, long expireAtMillis) {
    }
}