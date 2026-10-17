package com.campus.charity.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** 跨域来源配置，生产环境只允许学校前端域名。 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.web")
public class WebProperties {

    /** 逗号分隔的完整 Origin，例如 https://charity.example.edu。 */
    private String allowedOrigins = "http://localhost:9528,http://127.0.0.1:9528,http://10.*:9528,http://172.*:9528,http://192.168.*:9528";

    public List<String> allowedOriginList() {
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
    }
}
