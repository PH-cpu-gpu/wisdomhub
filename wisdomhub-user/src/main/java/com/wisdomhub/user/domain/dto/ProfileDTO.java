package com.wisdomhub.user.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileDTO {

    @Size(max = 20, message = "用户名长度不能超过20")
    private String username;

    @Size(max = 255, message = "简介长度不能超过255")
    private String bio;
}
