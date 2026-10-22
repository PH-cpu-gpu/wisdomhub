package com.wisdomhub.search.service.reconcile;

import com.wisdomhub.common.mq.event.UserUpdatedEvent;
import com.wisdomhub.common.mq.event.payload.UserEventPayload;
import com.wisdomhub.common.mq.util.EventPublisher;
import com.wisdomhub.search.api.UserFeign;
import com.wisdomhub.search.api.dto.UserSyncDTO;
import com.wisdomhub.search.config.ReconcileProperties;
import com.wisdomhub.search.domain.document.UserIndexDoc;
import com.wisdomhub.search.service.EsIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * user_index 对账：以 user 表为准，比对 username / avatar / bio，不一致就重投 user.updated。
 * 没有 user.deleted，孤儿文档在全量模式里直接删。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserReconcileService {

    private static final String EPOCH = "1970-01-01T00:00:00";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final UserFeign userFeign;
    private final EsIndexService esIndexService;
    private final EventPublisher eventPublisher;
    private final ReconcileProperties properties;

    public ReconcileResult reconcile(boolean full) {
        ReconcileResult result = new ReconcileResult();
        Set<Long> sourceIds = new HashSet<>();
        String since = full
                ? EPOCH
                : LocalDateTime.now(ZoneOffset.UTC).minusMinutes(properties.getLookbackMinutes()).format(ISO);
        long lastId = 0L;

        while (true) {
            List<UserSyncDTO> page = userFeign.syncUsers(since, lastId, properties.getPageSize());
            if (page.isEmpty()) {
                break;
            }
            result.setScanned(result.getScanned() + page.size());
            List<Long> ids = page.stream().map(UserSyncDTO::getUserId).filter(Objects::nonNull).toList();
            Map<Long, UserIndexDoc> docs = esIndexService.mgetUsers(ids);
            for (UserSyncDTO user : page) {
                if (user.getUserId() == null) {
                    continue;
                }
                sourceIds.add(user.getUserId());
                compareAndPublish(user, docs.get(user.getUserId()), result);
            }
            UserSyncDTO last = page.getLast();
            if (last.getUpdateTime() == null) {
                log.warn("用户 update_time 为空，停止本页推进: userId={}", last.getUserId());
                break;
            }
            since = last.getUpdateTime().format(ISO);
            lastId = last.getUserId();
            if (page.size() < properties.getPageSize()) {
                break;
            }
        }

        if (full) {
            scanOrphans(sourceIds, result);
        }
        return result;
    }

    private void compareAndPublish(UserSyncDTO user, UserIndexDoc doc, ReconcileResult result) {
        // 不比 updateTime：bindEmail 会 bump 它但不发 user.updated
        if (doc == null || !sameUser(user, doc)) {
            result.setMismatched(result.getMismatched() + 1);
            eventPublisher.send(new UserUpdatedEvent(UserEventPayload.builder()
                    .userId(user.getUserId())
                    .username(user.getUsername())
                    .avatar(user.getAvatar())
                    .bio(user.getBio())
                    .createTime(user.getCreateTime())
                    .updateTime(user.getUpdateTime())
                    .build()));
            result.setRepublished(result.getRepublished() + 1);
        }
    }

    private boolean sameUser(UserSyncDTO source, UserIndexDoc doc) {
        return Objects.equals(source.getUsername(), doc.getUsername())
                && Objects.equals(source.getAvatar(), doc.getAvatar())
                && Objects.equals(source.getBio(), doc.getBio());
    }

    /** 全量模式才做：ES 里有、user 表里没有的文档直接删，没有删除事件可重投 */
    private void scanOrphans(Set<Long> sourceIds, ReconcileResult result) {
        String after = null;
        while (true) {
            List<Long> ids = esIndexService.scanUserIds(after, properties.getEsScanSize());
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                if (!sourceIds.contains(id)) {
                    esIndexService.deleteUser(id);
                    result.setOrphans(result.getOrphans() + 1);
                }
            }
            after = String.valueOf(ids.getLast());
            if (ids.size() < properties.getEsScanSize()) {
                break;
            }
        }
    }
}
