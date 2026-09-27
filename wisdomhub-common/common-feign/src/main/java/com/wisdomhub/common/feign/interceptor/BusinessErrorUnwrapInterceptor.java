package com.wisdomhub.common.feign.interceptor;

import com.wisdomhub.common.core.exception.BusinessException;
import feign.InvocationContext;
import feign.ResponseInterceptor;
import feign.codec.DecodeException;

/**
 * 把 Decoder 抛出的业务异常从 Feign 的包装里拆出来。
 */
public class BusinessErrorUnwrapInterceptor implements ResponseInterceptor {

    @Override
    public Object intercept(InvocationContext ctx, Chain chain) throws Exception {
        try {
            return chain.next(ctx);
        } catch (DecodeException e) {
            if (e.getCause() instanceof BusinessException businessException) {
                throw businessException;
            }
            throw e;
        }
    }
}
