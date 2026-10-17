package com.campus.charity;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 校园公益众筹存证溯源系统 - 后端启动类。
 *
 * <p>技术栈：SpringBoot + Spring Security(JWT/RBAC) + MyBatis-Plus + MySQL
 * + Hyperledger Fabric Gateway（Go 链码 charity-cc）。</p>
 */
@SpringBootApplication
@MapperScan("com.campus.charity.module.**.mapper")
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class CampusCharityApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusCharityApplication.class, args);
        System.out.println("\n=============================================================");
        System.out.println(" 校园公益众筹存证溯源系统后端启动成功");
        System.out.println(" 接口文档: http://localhost:8080/doc.html");
        System.out.println(" 仅校内使用，禁止对外公开募捐；仅文件哈希摘要上链");
        System.out.println("=============================================================\n");
    }
}
