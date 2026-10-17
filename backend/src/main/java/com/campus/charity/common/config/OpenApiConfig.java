package com.campus.charity.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Knife4j / OpenAPI 3）。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI campusCharityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("校园公益众筹存证溯源系统接口文档")
                        .description("用户 / 众筹项目 / 捐赠业务 / 区块链存证 / 溯源查询 / 后台监管审计")
                        .version("1.0.0"))
                .components(new Components().addSecuritySchemes("Bearer",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"));
    }
}
