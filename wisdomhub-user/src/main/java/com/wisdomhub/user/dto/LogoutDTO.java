package com.wisdomhub.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutDTO {

    @NotBlank(message = "accessToken不能为空")
    private String accessToken;

    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
