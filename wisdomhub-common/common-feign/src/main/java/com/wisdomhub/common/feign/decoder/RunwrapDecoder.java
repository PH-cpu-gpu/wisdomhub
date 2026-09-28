package com.wisdomhub.common.feign.decoder;

import com.wisdomhub.common.core.exception.BusinessException;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.R;
import com.wisdomhub.common.feign.exception.RemoteErrorCode;
import feign.FeignException;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

// 将R<VO> 解包为VO
public class RunwrapDecoder implements Decoder {

    private final Decoder delegate;

    public RunwrapDecoder(Decoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, DecodeException, FeignException {
        if (type == Void.class || type == Void.TYPE) {
            if (response.body() == null) {
                return null;
            }
            check((R<?>) delegate.decode(response, rTypeOf(Object.class)));
            return null;
        }

        if (isWrapper(type)) {
            return delegate.decode(response, type);
        }

        // 必须用方法声明的真实返回类型拼 R<真实类型>
        Object decoded = delegate.decode(response, rTypeOf(type));
        if (decoded == null) {
            return null;
        }
        R<?> r = (R<?>) decoded;
        check(r);
        return r.getData();
    }

    // 拼出 R<T> 这个泛型类型，交给 Jackson 反序列化
    private static Type rTypeOf(Type dataType) {
        return ResolvableType.forClassWithGenerics(R.class, ResolvableType.forType(dataType)).getType();
    }

    // 判断返回类型是不是spring需要特殊处理的类型
    private static boolean isWrapper(Type type) {
        if (!(type instanceof ParameterizedType pt)) {
            return type == Response.class;
        }
        Class<?> raw = (Class<?>) pt.getRawType();
        return raw == ResponseEntity.class || raw == Optional.class;
    }

    // 检测下游返回体是否符合R约定
    private static void check(R<?> r) {
        if (r == null || r.getCode() == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "下游返回体不符合 R 约定");
        }
        if (r.getCode() != CommonErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(RemoteErrorCode.of(r.getCode(), r.getMessage()));
        }
    }
}
