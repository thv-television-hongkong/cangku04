package com.campus.charity.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.jwt")
public class JwtProperties {

    private String secret = "campus-charity-default-secret-key-please-change-in-production";
    private Integer expireMinutes = 720;
    private String header = "Authorization";
    private String prefix = "Bearer ";
}
