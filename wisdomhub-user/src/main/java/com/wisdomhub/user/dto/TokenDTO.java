package com.wisdomhub.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenDTO {

    private String accessToken;

    private String refreshToken;
}
