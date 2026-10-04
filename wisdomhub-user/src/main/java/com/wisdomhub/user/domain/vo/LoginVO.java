package com.wisdomhub.user.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private String accessToken;
    private String refreshToken;
}
