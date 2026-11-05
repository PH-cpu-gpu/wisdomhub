package com.wisdomhub.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtPropertyConfiguration {

    private final String privateKey;

    public JwtPropertyConfiguration(@Value("${jwt.private-key}") String privateKey) {
        this.privateKey = privateKey.trim();
    }

    public String privateKey() {
        return this.privateKey;
    }
}
