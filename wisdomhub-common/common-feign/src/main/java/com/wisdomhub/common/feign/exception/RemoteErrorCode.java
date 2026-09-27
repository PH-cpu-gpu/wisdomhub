package com.wisdomhub.common.feign.exception;

import cn.hutool.core.util.StrUtil;
import com.wisdomhub.common.core.result.ErrorCode;

public record RemoteErrorCode(int code, String message) implements ErrorCode {

    public static RemoteErrorCode of(int code, String message) {
        return new RemoteErrorCode(code, StrUtil.blankToDefault(message, "下游返回错误"));
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
