package com.wisdomhub.common.security.filter;

import com.wisdomhub.common.redis.constants.RedisPrefix;
import com.wisdomhub.common.security.config.InternalSignProperties;
import com.wisdomhub.common.security.internal.CachedBodyHttpServletRequest;
import com.wisdomhub.common.security.constants.InternalSignConstants;
import com.wisdomhub.common.security.exception.InternalSignException;
import com.wisdomhub.common.security.util.InternalSignUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * /internal/** 统一验签：白名单 → 时间戳 → 签名 → Nonce。
 */
@Slf4j
public class InternalSignFilter extends OncePerRequestFilter {

    private static final Pattern NONCE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final byte[] UNAUTHORIZED_BODY =
            "{\"code\":401,\"message\":\"服务签名校验失败\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BODY_TOO_LARGE_BODY =
            "{\"code\":413,\"message\":\"请求体过大\",\"data\":null}".getBytes(StandardCharsets.UTF_8);

    private final InternalSignProperties properties;
    private final StringRedisTemplate redisTemplate;

    public InternalSignFilter(InternalSignProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = appPath(request);
        return !path.equals(InternalSignConstants.PATH_PREFIX) && !path.startsWith(InternalSignConstants.PATH_PREFIX + "/");
    }

    /** 有 context-path 时 getRequestURI 带前缀，匹配要按应用内路径来。 */
    private String appPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CachedBodyHttpServletRequest cachedRequest;
        try {
            cachedRequest = new CachedBodyHttpServletRequest(request, properties.getMaxBodyBytes());
        } catch (CachedBodyHttpServletRequest.BodyTooLargeException e) {
            writeReject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, BODY_TOO_LARGE_BODY);
            return;
        }

        try {
            verify(cachedRequest);
        } catch (InternalSignException e) {
            log.warn("内部签名校验失败: reason={}, service={}, {} {}",
                    e.getReason(), request.getHeader(InternalSignConstants.HEADER_SERVICE_NAME),
                    request.getMethod(), request.getRequestURI());
            writeReject(response, HttpServletResponse.SC_UNAUTHORIZED, UNAUTHORIZED_BODY);
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }

    private void verify(HttpServletRequest request) {
        String serviceName = request.getHeader(InternalSignConstants.HEADER_SERVICE_NAME);
        if (!StringUtils.hasText(serviceName)) {
            throw new InternalSignException("缺少服务名");
        }
        List<String> allowedServices = properties.getAllowedServices();
        if (allowedServices != null && !allowedServices.isEmpty() && !allowedServices.contains(serviceName)) {
            throw new InternalSignException("服务名不在白名单");
        }

        String timestampText = request.getHeader(InternalSignConstants.HEADER_TIMESTAMP);
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException e) {
            throw new InternalSignException("时间戳非法");
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > properties.getTimestampWindowSeconds()) {
            throw new InternalSignException("时间戳超出窗口");
        }

        String nonce = request.getHeader(InternalSignConstants.HEADER_NONCE);
        if (nonce == null || !NONCE_PATTERN.matcher(nonce).matches()) {
            throw new InternalSignException("Nonce 非法");
        }

        String signature = request.getHeader(InternalSignConstants.HEADER_SIGNATURE);
        String pathWithQuery = request.getRequestURI();
        String queryString = request.getQueryString();
        if (StringUtils.hasText(queryString)) {
            pathWithQuery = pathWithQuery + "?" + queryString;
        }
        String canonical = InternalSignUtil.canonicalString(request.getMethod(), pathWithQuery,
                timestampText, nonce, ((CachedBodyHttpServletRequest) request).getBody());
        if (!InternalSignUtil.verify(canonical, properties.getSecret(), signature)) {
            throw new InternalSignException("签名不匹配");
        }

        Boolean firstUse;
        try {
            firstUse = redisTemplate.opsForValue().setIfAbsent(
                    RedisPrefix.INTERNAL + InternalSignConstants.NONCE_KEY + nonce,
                    "1", Duration.ofSeconds(properties.getNonceTtlSeconds()));
        } catch (Exception e) {
            log.error("Nonce 去重失败，按拒绝处理", e);
            throw new InternalSignException("Nonce 存储不可用");
        }
        if (!Boolean.TRUE.equals(firstUse)) {
            throw new InternalSignException("Nonce 重复");
        }
    }

    private void writeReject(HttpServletResponse response, int status, byte[] body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
