package com.wisdomhub.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 绑定 / 改绑邮箱。必须带验证码 —— 只凭一个邮箱串就能绑，等于允许绑别人的邮箱 */
@Data
public class BindEmailDTO {

    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
