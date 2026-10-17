package com.campus.charity.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：跨域（前端 9528 访问后端 8080）+ 上传文件静态映射。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final FileProperties fileProperties;
    private final WebProperties webProperties;

    public WebMvcConfig(FileProperties fileProperties, WebProperties webProperties) {
        this.fileProperties = fileProperties;
        this.webProperties = webProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(webProperties.allowedOriginList().toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/file/preview/**")
                .addResourceLocations("file:" + fileProperties.getUploadPath() + "/");
    }
}
