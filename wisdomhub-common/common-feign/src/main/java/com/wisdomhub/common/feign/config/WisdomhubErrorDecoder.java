package com.wisdomhub.common.feign.config;

import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.feign.exception.RemoteErrorCode;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

public class WisdomhubErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder delegate = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        Exception original = delegate.decode(methodKey, response);
        // 保留 Feign的可重试语义
        if (original instanceof RetryableException) {
            return original;
        }
        return new BusinessException(new RemoteErrorCode(response.status(), "下游接口 " + methodKey + " 返回 HTTP " + response.status()));
    }
}
