package com.wisdomhub.gateway.exception;

import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.ErrorCode;
import com.wisdomhub.common.core.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 网关自身的异常出口。
 *
 * <p>body 与各业务服务的 GlobalExceptionHandler 保持同一种形状（{@code {code,message,data}}），
 * 但 HTTP 状态码如实返回：网关是系统的边界，404 / 503 必须能在监控与浏览器网络面板里看见，
 * 不能抹成 200。代价是前端对网关自身的错误要走 axios 的 err 分支读 {@code err.response.data}。
 *
 * <p>本类放在网关模块而不是 common-core：它只服务于网关，且依赖 WebFlux 的
 * {@link ResponseStatusException}，放进公共模块会把 WebFlux 语义传染给 Servlet 服务。
 */
@Slf4j
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<R<Object>> handleResponseStatus(ResponseStatusException e) {
        HttpStatusCode status = e.getStatusCode();
        log.warn("网关返回 {}: {}", status.value(), e.getReason());
        return ResponseEntity.status(status).body(R.error(toErrorCode(status)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Object>> handleException(Exception e) {
        log.error("网关系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.error(CommonErrorCode.INTERNAL_ERROR));
    }

    /** 错误码按 HTTP 语义取名，取不到同值的就按 4xx / 5xx 归类 */
    private static ErrorCode toErrorCode(HttpStatusCode status) {
        int value = status.value();
        for (CommonErrorCode code : CommonErrorCode.values()) {
            if (code.getCode() == value) {
                return code;
            }
        }
        return value >= 500 ? CommonErrorCode.INTERNAL_ERROR : CommonErrorCode.BAD_REQUEST;
    }
}
