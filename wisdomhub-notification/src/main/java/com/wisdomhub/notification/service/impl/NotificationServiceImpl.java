package com.wisdomhub.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.web.util.CurrentUserUtil;
import com.wisdomhub.common.web.util.PageUtil;
import com.wisdomhub.notification.domain.entity.Notification;
import com.wisdomhub.notification.domain.entity.NotificationSetting;
import com.wisdomhub.notification.domain.vo.NotificationVO;
import com.wisdomhub.notification.mapper.NotificationMapper;
import com.wisdomhub.notification.mapper.NotificationSettingMapper;
import com.wisdomhub.notification.service.NotificationService;
import com.wisdomhub.notification.service.UnreadCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationSettingMapper notificationSettingMapper;
    private final UnreadCountService unreadCountService;

    @Override
    public Notification create(Long userId, Long senderId, int type, int targetType, Long targetId) {
        if (userId == null) {
            throw new IllegalArgumentException("通知接收者缺失");
        }
        if (userId.equals(senderId)) {
            log.debug("自己触发自己, 不落通知: userId={}, type={}", userId, type);
            return null;
        }
        if (!enabled(userId, type)) {
            log.debug("接收者关闭了该类型通知: userId={}, type={}", userId, type);
            return null;
        }
        Notification notification = Notification.builder()
                .userId(userId)
                .senderId(senderId)
                .type((byte) type)
                .targetType((byte) targetType)
                .targetId(targetId)
                .content("")
                .isRead((byte) 0)
                .build();
        notificationMapper.insert(notification);
        // 计数器失败只记日志，绝不能抛出去：消费者会删去重 key 重试，导致重复通知
        unreadCountService.increment(userId);
        return notification;
    }

    @Override
    public PageResponse<NotificationVO> page(Integer page, Integer pageSize) {
        Long userId = CurrentUserUtil.id();
        IPage<Notification> result = notificationMapper.selectPage(PageUtil.of(page, pageSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime)
                        .orderByDesc(Notification::getId));
        List<NotificationVO> items = result.getRecords().stream().map(this::toVO).toList();
        return PageUtil.toResponse(result, items);
    }

    @Override
    public void markRead(Long notificationId) {
        Long userId = CurrentUserUtil.id();
        int affected = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .set(Notification::getIsRead, (byte) 1)
                // update(null, wrapper) 不触发自动填充，自己补 update_time
                .set(Notification::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, (byte) 0));
        if (affected == 0) {
            Notification existing = notificationMapper.selectById(notificationId);
            if (existing == null || !userId.equals(existing.getUserId())) {
                throw new BusinessException(CommonErrorCode.NOT_FOUND, "通知不存在");
            }
            // 已经读过：幂等返回，不重复减计数
            return;
        }
        unreadCountService.decrement(userId);
    }

    @Override
    public void markAllRead() {
        Long userId = CurrentUserUtil.id();
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .set(Notification::getIsRead, (byte) 1)
                .set(Notification::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC))
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, (byte) 0));
        // 用 DEL 而不是 SET 0：UPDATE 与 SET 0 之间若有新通知 INCR 进来会被抹掉
        unreadCountService.evict(userId);
    }

    @Override
    public int getUnreadCount() {
        return unreadCountService.get(CurrentUserUtil.id());
    }

    private NotificationVO toVO(Notification notification) {
        return NotificationVO.from(notification);
    }

    /** 缺行 = 开启 */
    private boolean enabled(Long userId, int type) {
        NotificationSetting setting = notificationSettingMapper.selectOne(
                new LambdaQueryWrapper<NotificationSetting>()
                        .eq(NotificationSetting::getUserId, userId)
                        .eq(NotificationSetting::getType, (byte) type));
        return setting == null || setting.getEnabled() == null || setting.getEnabled() != 0;
    }
}
