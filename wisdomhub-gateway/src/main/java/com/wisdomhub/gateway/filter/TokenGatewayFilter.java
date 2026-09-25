package com.wisdomhub.gateway.filter;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.wisdomhub.common.security.constants.SecurityConstants;
import com.wisdomhub.common.security.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class TokenGatewayFilter implements GlobalFilter, Ordered {

    @Value("${jwt.public-key}")
    private String publicKey;
    @Value("${url.no-auth-paths}")
    private String[] noAuthPaths;

    private static final byte[] BODY = """
            {
                "code":401,
                "message":"未登录或者登录已过期",
                "data":null
            }
            """.getBytes(StandardCharsets.UTF_8);

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 无条件剥离请求的X-USER-ID
        ServerHttpRequest cleanRequest = exchange.getRequest().mutate()
                .headers(h -> h.remove(SecurityConstants.X_USER_ID))
                .build();
        ServerWebExchange cleanExchange = exchange.mutate().request(cleanRequest).build();

        // 跨域预检, 无条件放行
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(cleanExchange);
        }

        String path = exchange.getRequest().getPath().toString();
        if (StrUtil.startWithAny(path, noAuthPaths)) {
            return chain.filter(cleanExchange);
        }

        String tokenWithPrefix = exchange.getRequest().getHeaders().getFirst(SecurityConstants.TOKEN_HEAD_NAME);
        if (StrUtil.isEmpty(tokenWithPrefix)) {
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(BODY);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }

        String token = StrUtil.removePrefix(tokenWithPrefix, SecurityConstants.TOKEN_PREFIX);
        Map<String, Object> claims = JwtUtil.parseToken(token, publicKey);
        if (ObjectUtil.isEmpty(claims)) {
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(BODY);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }

        Object userIdObj = claims.get(SecurityConstants.USER_ID);
        if (userIdObj == null) {
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(BODY);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
        String userId = String.valueOf(userIdObj);

        //TODO 检查redis黑名单

        ServerHttpRequest requestWithUserId = cleanExchange.getRequest().mutate()
                .header(SecurityConstants.X_USER_ID, userId)
                .build();
        return chain.filter(cleanExchange.mutate().request(requestWithUserId).build());
    }
}
