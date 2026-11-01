package com.wisdomhub.common.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员名单，由 Nacos 的 wisdomhub-env.yaml 下发；为空表示没有人拥有管理权限。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "wisdomhub.admin")
public class AdminProperties {

    private List<Long> userIds = new ArrayList<>();

    public boolean contains(Long userId) {
        return userId != null && userIds != null && userIds.contains(userId);
    }
}
