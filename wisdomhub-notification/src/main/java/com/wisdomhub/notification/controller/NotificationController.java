package com.wisdomhub.notification.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.notification.domain.vo.NotificationVO;
import com.wisdomhub.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "通知controller")
@Validated
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "未读数")
    @GetMapping("/unread/count")
    public R<Integer> unreadCount() {
        return R.success(notificationService.getUnreadCount());
    }

    @Operation(summary = "通知列表")
    @GetMapping("/list")
    public R<PageResponse<NotificationVO>> list(@RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(notificationService.page(page, pageSize));
    }

    @Operation(summary = "标记单条已读")
    @PutMapping("/{notificationId}/read")
    public R<Void> markRead(@NotNull @PathVariable Long notificationId) {
        notificationService.markRead(notificationId);
        return R.success();
    }

    @Operation(summary = "全部已读")
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        notificationService.markAllRead();
        return R.success();
    }
}
