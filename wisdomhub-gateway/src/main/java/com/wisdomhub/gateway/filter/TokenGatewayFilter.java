package com.wisdomhub.gateway.filter;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.wisdomhub.common.redis.constants.RedisSharedConstants;
import com.wisdomhub.common.security.constants.SecurityConstants;
import com.wisdomhub.common.security.util.JwtUtil;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "url")
public class TokenGatewayFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @Value("${jwt.public-key}")
    private String publicKey;
    /** 免登录路径 */
    @Setter
    private String[] noAuthPaths;
    /** 可选登录路径 */
    @Setter
    private String[] optionalAuthPaths;

    private static final byte[] BODY = """
            {
                "code":401,
                "message":"未登录或者登录已过期",
                "data":null
            }
            """.getBytes(StandardCharsets.UTF_8);

    @Autowired
    public TokenGatewayFilter(ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    /** 未登录 / token 失效：状态码如实返回 401，body 与各服务的 R 形状一致 */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(BODY);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 是否命中可选登录名单。
     * 跳过空白项：名单若被误配成 ""，startWith 对空前缀恒真，会把全站鉴权降成可选。
     */
    private boolean optionalAuthMatched(String path) {
        if (ObjectUtil.isEmpty(optionalAuthPaths)) {
            return false;
        }
        for (String prefix : optionalAuthPaths) {
            if (StrUtil.isNotBlank(prefix) && StrUtil.startWith(path, prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 可选登录：能解析出合法且未注销的 token 就注入 X-User-Id，否则按游客放行，绝不 401 */
    private Mono<Void> filterOptionalAuth(ServerWebExchange exchange, GatewayFilterChain chain) {
        String tokenWithPrefix = exchange.getRequest().getHeaders().getFirst(SecurityConstants.TOKEN_HEAD_NAME);
        if (StrUtil.isEmpty(tokenWithPrefix)) {
            return chain.filter(exchange);
        }
        String token = StrUtil.removePrefix(tokenWithPrefix, SecurityConstants.TOKEN_PREFIX);
        Map<String, Object> claims = JwtUtil.parseToken(token, publicKey);
        Object userIdObj = ObjectUtil.isEmpty(claims) ? null : claims.get(SecurityConstants.USER_ID);
        if (userIdObj == null) {
            return chain.filter(exchange);
        }
        String blacklistKey = RedisSharedConstants.BLACKLIST_KEY + DigestUtil.sha256Hex(token);
        return reactiveStringRedisTemplate.hasKey(blacklistKey)
                .defaultIfEmpty(false)
                .flatMap(blacklisted -> Boolean.TRUE.equals(blacklisted)
                        ? chain.filter(exchange)
                        : chain.filter(exchange.mutate().request(exchange.getRequest().mutate()
                                .header(SecurityConstants.X_USER_ID, String.valueOf(userIdObj)).build()).build()));
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

        // 检验白名单：明确声明的免登录路径，连 token 都不解析
        if (StrUtil.startWithAny(path, noAuthPaths)) {
            return chain.filter(cleanExchange);
        }

        // 可选登录：游客放行，带合法 token 才透传 X-User-Id
        if (optionalAuthMatched(path)) {
            return filterOptionalAuth(cleanExchange, chain);
        }

        // 检验token是否为空
        String tokenWithPrefix = exchange.getRequest().getHeaders().getFirst(SecurityConstants.TOKEN_HEAD_NAME);
        if (StrUtil.isEmpty(tokenWithPrefix)) {
            return unauthorized(exchange);
        }

        // 检验token
        String token = StrUtil.removePrefix(tokenWithPrefix, SecurityConstants.TOKEN_PREFIX);
        Map<String, Object> claims = JwtUtil.parseToken(token, publicKey);
        if (ObjectUtil.isEmpty(claims)) {
            return unauthorized(exchange);
        }

        // 解析出用户ID
        Object userIdObj = claims.get(SecurityConstants.USER_ID);
        if (userIdObj == null) {
            return unauthorized(exchange);
        }
        String userId = String.valueOf(userIdObj);

        // 将用户ID存入请求头传入下游
        ServerHttpRequest requestWithUserId = cleanExchange.getRequest().mutate()
                .header(SecurityConstants.X_USER_ID, userId)
                .build();

        // 检查token是否位于redis黑名单; 网关是响应式栈, 必须用响应式客户端, 阻塞式会占住事件循环线程
        String blacklistKey = RedisSharedConstants.BLACKLIST_KEY + DigestUtil.sha256Hex(token);
        return reactiveStringRedisTemplate.hasKey(blacklistKey)
                .defaultIfEmpty(false)
                .flatMap(blacklisted -> Boolean.TRUE.equals(blacklisted)
                        ? unauthorized(exchange)
                        : chain.filter(cleanExchange.mutate().request(requestWithUserId).build()));
    }
}
