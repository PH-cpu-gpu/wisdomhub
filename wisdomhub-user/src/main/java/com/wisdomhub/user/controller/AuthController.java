package com.wisdomhub.user.controller;

import com.wisdomhub.common.core.result.R;
import com.wisdomhub.user.dto.RegisterDTO;
import com.wisdomhub.user.service.UserService;
import com.wisdomhub.user.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户接口类")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;


    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public R<LoginVO> register(@Validated @RequestBody RegisterDTO registerDTO) {
        return R.success(userService.register(registerDTO));
    }

    // 这里先模拟验证码的实现, 先将验证码记录在日志
    @Operation(summary = "通过手机号获取验证码")
    @PostMapping("/code/phone")
    public R<Void> sendCodeByPhone(@NotBlank(message = "手机号不能为空")
                                     @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
                                     @RequestParam String phone) {
        userService.sendCodeByPhone(phone);
        return R.success();
    }


    // 这里先模拟验证码的实现, 先将验证码记录在日志
    @Operation(summary = "通过邮箱获取验证码")
    @PostMapping("/code/email")
    public R<Void> sendCodeByEmail(@NotBlank(message = "邮箱号不能为空")
                                     @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "邮箱号格式不正确")
                                     @RequestParam String email) {
        userService.sendCodeByEmail(email);
        return R.success();
    }
}
