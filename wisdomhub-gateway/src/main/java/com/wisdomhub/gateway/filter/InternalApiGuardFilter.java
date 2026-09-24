package com.wisdomhub.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalApiGuardFilter implements WebFilter {

    private static final byte[] BODY = """
            {
                "code":403,
                "message":"内部接口不对外暴露",
                "data":null
            }
            """.getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!"/internal".equals(path) && !path.startsWith("/internal/")) {
            return chain.filter(exchange);
        }
        log.warn("拦截公网 /internal 访问: {} {}", exchange.getRequest().getMethod(), path);
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(BODY);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
