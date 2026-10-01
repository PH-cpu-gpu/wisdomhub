package com.wisdomhub.user.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.user.dto.ProfileDTO;
import com.wisdomhub.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "用户控制器")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "更新用户资料")
    @PutMapping("/profile")
    public R<Void> updateProfile(@Valid @RequestBody ProfileDTO profileDTO) {
        userService.updateProfile(profileDTO);
        return R.success();
    }

    @Operation(summary = "更新用户头像")
    @PostMapping("/avatar")
    public R<String> updateAvatar(@RequestParam("file") MultipartFile file) {
        return R.success(userService.updateAvatar(file));
    }
}
