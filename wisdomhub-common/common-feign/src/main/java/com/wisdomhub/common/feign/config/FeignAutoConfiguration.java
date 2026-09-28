package com.wisdomhub.common.feign.config;

import com.wisdomhub.common.feign.decoder.RunwrapDecoder;
import com.wisdomhub.common.feign.decoder.WisdomhubErrorDecoder;
import com.wisdomhub.common.feign.exception.FeignExceptionHandler;
import com.wisdomhub.common.feign.interceptor.BusinessErrorUnwrapInterceptor;
import com.wisdomhub.common.feign.interceptor.UserContextInterceptor;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.optionals.OptionalDecoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignAutoConfiguration {

    // 身份透传拦截器配置
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public RequestInterceptor userContextInterceptor() {
        return new UserContextInterceptor();
    }

    // R解包器配置
    @Bean
    @ConditionalOnMissingBean(Decoder.class)
    public Decoder wisdomhubFeignDecoder(ObjectFactory<HttpMessageConverters> messageConverters,
                                         ObjectProvider<HttpMessageConverterCustomizer> customizers) {
        Decoder delegate = new OptionalDecoder(
                new ResponseEntityDecoder(new SpringDecoder(messageConverters, customizers)));
        return new RunwrapDecoder(delegate);
    }

    // 非200错误解包器配置
    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public ErrorDecoder wisdomhubErrorDecoder() {
        return new WisdomhubErrorDecoder();
    }

    // 打开 void 返回值的解码 + 把业务异常从 Feign 的包装里拆出来
    @Bean
    public FeignBuilderCustomizer wisdomhubFeignBuilderCustomizer() {
        return builder -> builder
                .decodeVoid()
                .responseInterceptor(new BusinessErrorUnwrapInterceptor());
    }

    // 异常处理器配置
    // 此处两个注解表示只有当前项目是Servlet Web项目时, 才启用这个配置类
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class FeignExceptionAdviceConfiguration {
        @Bean
        public FeignExceptionHandler feignExceptionAdvice() {
            return new FeignExceptionHandler();
        }
    }
}