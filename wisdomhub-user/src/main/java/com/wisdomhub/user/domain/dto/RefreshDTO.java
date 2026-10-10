package com.wisdomhub.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 刷新入参。走 JSON body，与 logout 统一（原先 refresh 是 @RequestParam，两个 token 接口风格不一致） */
@Data
public class RefreshDTO {

    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
