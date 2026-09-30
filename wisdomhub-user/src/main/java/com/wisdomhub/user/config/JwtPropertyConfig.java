package com.wisdomhub.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtPropertyConfig {

    private final String privateKey;

    public JwtPropertyConfig(@Value("classpath:private.key") Resource resource) throws IOException {
        this.privateKey = resource.getContentAsString(StandardCharsets.UTF_8).trim();
    }

    public String privateKey() {
        return this.privateKey;
    }
}
