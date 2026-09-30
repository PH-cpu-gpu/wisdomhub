package com.wisdomhub.common.web.exception;

import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.R;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<Object> businessException(BusinessException e) {
        log.warn("业务异常, 错误码: {}, 错误信息: {}", e.getErrorCode().getCode(), e.getMessage());
        return R.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public R<Object> handleValidation(BindException e) {
        String detail = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return R.error(CommonErrorCode.BAD_REQUEST, detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Object> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return R.error(CommonErrorCode.BAD_REQUEST, "缺少参数: " + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Object> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return R.error(CommonErrorCode.BAD_REQUEST, "请求体格式不正确");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public R<Object> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("发生唯一索引冲突, 错误信息: {}", e.getMessage());
        return R.error(CommonErrorCode.CONFLICT);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public R<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        String detail = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return R.error(CommonErrorCode.BAD_REQUEST, detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<Object> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
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
