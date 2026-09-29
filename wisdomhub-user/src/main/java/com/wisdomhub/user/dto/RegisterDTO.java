package com.wisdomhub.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterDTO {

    private String phone;

    private String email;

    @NotBlank
    private String code;

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
