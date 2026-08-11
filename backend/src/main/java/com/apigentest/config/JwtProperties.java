package com.apigentest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项（application.yml 中 jwt.*）
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;

    private int expireHours = 24;
}