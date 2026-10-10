package com.wisdomhub.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdomhub.common.core.result.CommonErrorCode;
import com.wisdomhub.common.core.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 给 RateLimiter 的默认返回 补 body
 */
@Slf4j
@Component
public class RateLimitResponseFilter implements GlobalFilter, Ordered {

    /** 启动时序列化一次，避免每个被限流的请求都过一遍 Jackson；形状与 CommonErrorCode 一致，不会漂 */
    private final byte[] body;

    public RateLimitResponseFilter(ObjectMapper objectMapper) {
        try {
            this.body = objectMapper.writeValueAsBytes(R.error(CommonErrorCode.TOO_MANY_REQUESTS));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("429 响应体序列化失败", e);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse original = exchange.getResponse();

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> setComplete() {
                if (getStatusCode() != null && getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                    getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    DataBuffer buffer = bufferFactory().wrap(body);
                    // 走 writeWith 而不是 setComplete，否则 body 写不出去
                    return super.writeWith(Mono.just(buffer));
                }
                return super.setComplete();
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
