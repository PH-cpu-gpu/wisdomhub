package com.wisdomhub.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutAllDTO {

    @NotBlank(message = "accessToken不能为空")
    private String accessToken;
}
