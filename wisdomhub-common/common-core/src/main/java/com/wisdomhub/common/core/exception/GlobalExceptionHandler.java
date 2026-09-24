package com.wisdomhub.common.core.exception;

import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<Object> businessException(BusinessException e) {
        log.warn("业务异常, 错误码: {}, 错误信息: {}", e.getErrorCode().getCode(), e.getMessage());
        return R.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Object> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return R.error(CommonErrorCode.BAD_REQUEST, detail);
    }

    @ExceptionHandler(Exception.class)
    public R<Object> exception(Exception e) {
        log.error("系统异常", e);
        return R.error(CommonErrorCode.INTERNAL_ERROR);
    }

}
