package com.wisdomhub.user.controller;

import com.wisdomhub.common.core.result.PageResponse;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.user.domain.dto.BindEmailDTO;
import com.wisdomhub.user.domain.dto.ProfileDTO;
import com.wisdomhub.user.domain.vo.UserBriefVO;
import com.wisdomhub.user.domain.vo.UserProfileVO;
import com.wisdomhub.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "用户controller")
@Validated
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "查询用户个人主页")
    @GetMapping("/profile/{userId}")
    public R<UserProfileVO> getUserProfile(@NotNull @PathVariable Long userId) {
        return R.success(userService.getUserProfile(userId));
    }

    /**
     * 批量取用户摘要（列表页显示作者名用）。
     * ⚠️ 路径落在 `/profile/brief` 而不是 `/brief`：网关的免登录名单里是 `/api/user/profile/`
     * 前缀，这样游客也能取（文章列表对游客可见）。字面量 `brief` 优先于 `/profile/{userId}`，
     * 不会撞车。
     */
    @Operation(summary = "批量取用户摘要")
    @GetMapping("/profile/brief")
    public R<List<UserBriefVO>> listBrief(@RequestParam("userIds") List<Long> userIds) {
        return R.success(userService.listBrief(userIds));
    }

    @Operation(summary = "更新用户资料")
    @PutMapping("/profile")
    public R<Void> updateProfile(@Valid @RequestBody ProfileDTO profileDTO) {
        userService.updateProfile(profileDTO);
        return R.success();
    }

    @Operation(summary = "绑定 / 改绑邮箱")
    @PutMapping("/email")
    public R<Void> bindEmail(@Valid @RequestBody BindEmailDTO bindEmailDTO) {
        userService.bindEmail(bindEmailDTO);
        return R.success();
    }

    @Operation(summary = "更新用户头像")
    @PostMapping("/avatar")
    public R<String> updateAvatar(@RequestParam("file") MultipartFile file) {
        return R.success(userService.updateAvatar(file));
    }

    @Operation(summary = "关注")
    @PutMapping("/follow/{userId}")
    public R<Void> follow(@NotNull @PathVariable Long userId) {
        userService.follow(userId);
        return R.success();
    }

    @Operation(summary = "取关")
    @DeleteMapping("/follow/{userId}")
    public R<Void> unfollow(@NotNull @PathVariable Long userId) {
        userService.unfollow(userId);
        return R.success();
    }

    @Operation(summary = "是否已关注")
    @GetMapping("/follow/{userId}")
    public R<Boolean> isFollowed(@NotNull @PathVariable Long userId) {
        return R.success(userService.isFollowed(userId));
    }

    @Operation(summary = "关注列表")
    @GetMapping("/follow/list")
    public R<PageResponse<UserBriefVO>> listFollowing(@RequestParam Long userId,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(userService.listFollowing(userId, page, pageSize));
    }

    @Operation(summary = "粉丝列表")
    @GetMapping("/fans/list")
    public R<PageResponse<UserBriefVO>> listFans(@RequestParam Long userId,
                                                 @RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.success(userService.listFans(userId, page, pageSize));
    }
}
