package com.wisdomhub.user.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.user.domain.vo.UserSyncVO;
import com.wisdomhub.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "服务间用户controller")
@Validated
@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @Operation(summary = "按 (updateTime, id) 游标增量分页，供 search 对账；since 省略表示从头全表扫")
    @GetMapping("/sync")
    public R<List<UserSyncVO>> sync(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "0") Long lastId,
            @RequestParam(required = false) Integer size) {
        return R.success(userService.syncUsers(since, lastId, size));
    }
}
