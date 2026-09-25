package com.wisdomhub.common.security.util;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.crypto.asymmetric.RSA;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类, 可以创建, 解析,校验token, 使用RSA非对称加密
 * 密钥按照纯Base64存储
 */
@Slf4j
public class JwtUtil {

    private JwtUtil() {}

    public static String createToken(Map<String, Object> claims, String privateKey, int dateOffset, DateField dateField) {
        RSA rsa = new RSA(privateKey, null);
        return Jwts.builder().claims(claims)
                .signWith(rsa.getPrivateKey(), Jwts.SIG.RS256)
                .expiration(DateUtil.offset(new Date(), dateField, dateOffset))
                .compact();
    }

    public static String createToken(Map<String, Object> claims, String privateKey, int dateOffset) {
        return createToken(claims, privateKey, dateOffset, DateField.HOUR_OF_DAY);
    }

    public static Map<String, Object> parseToken(String token, String publicKey) {
        Map<String, Object> claims = new HashMap<>();
        try {
            RSA rsa = new RSA(null, publicKey);
            claims = Jwts.parser()
                    .verifyWith(rsa.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("token已经过期！");
        } catch (Exception e) {
            log.warn("token非法传入!", e);
        }
        return claims;
    }
}
