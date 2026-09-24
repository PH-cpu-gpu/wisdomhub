package com.wisdomhub.common.core.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "统一响应返回体。HTTP 状态码恒为 200，成败由 code 表达")
public class R<T> {

    @Schema(description = "状态码，200 表示成功", example = "200")
    private Integer code;

    @Schema(description = "提示文案，code != 200 时供前端展示", example = "ok")
    private String message;

    @Schema(description = "业务数据，失败时为 null")
    private T data;

    public static <T> R<T> success(T data) {
        return build(CommonErrorCode.SUCCESS.getCode(), CommonErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> R<T> success() {
        return success(null);
    }

    public static <T> R<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    public static <T> R<T> error(ErrorCode errorCode, String message) {
        return build(errorCode.getCode(), message, null);
    }

    public static <T> R<T> error(ErrorCode errorCode, String message, T data) {
        return build(errorCode.getCode(), message, data);
    }

    private static <T> R<T> build(int code, String message, T data) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.data = data;
        return r;
    }
}
