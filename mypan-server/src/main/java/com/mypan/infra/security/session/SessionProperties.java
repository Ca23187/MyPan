package com.mypan.infra.security.session;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "session")
public class SessionProperties {

    /**
     * Redis 登录态 TTL（滑动过期的“真实会话时长”）
     */
    private long ttlMillis = 604800000;
    private long renewThresholdMillis = 1800000;
}
