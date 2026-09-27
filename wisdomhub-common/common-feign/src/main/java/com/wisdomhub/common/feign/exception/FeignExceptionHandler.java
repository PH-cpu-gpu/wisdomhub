package com.wisdomhub.common.feign.exception;

import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.R;
import feign.FeignException;
import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeignExceptionHandler {

    // 连接超时, 读超时
    @ExceptionHandler(RetryableException.class)
    public R<Object> handleRetryableException(RetryableException e) {
        log.warn("调用下游失败：{}", e.getMessage());
        return R.error(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    // 状态码非200
    @ExceptionHandler(FeignException.class)
    public R<Object> handleFeignException(FeignException e) {
        log.warn("下游返回异常：status={}", e.status());
        return R.error(RemoteErrorCode.of(e.status() >= 400 ? e.status() : 503, "下游服务异常"));
    }
}
