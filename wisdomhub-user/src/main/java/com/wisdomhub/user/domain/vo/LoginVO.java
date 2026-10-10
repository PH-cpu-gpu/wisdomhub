package com.wisdomhub.user.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private String accessToken;
    private String refreshToken;

    /**
     * 当前账号是否已绑邮箱。前端 F1.1 据此决定要不要提示补绑。
     */
    private Boolean hasEmail;
}
