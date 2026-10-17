package com.campus.charity.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据库定时备份配置（可靠性需求：MySQL 业务数据定期备份）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.backup")
public class BackupProperties {

    private boolean enabled = true;
    /** 定时表达式，默认每日 02:00 */
    private String cron = "0 0 2 * * ?";
    private String dir = "./backup";
    private Integer retentionDays = 30;
    private String mysqldumpPath = "mysqldump";
    private String host = "127.0.0.1";
    private Integer port = 3306;
    private String database = "campus_charity";
    private String username = "root";
    private String password = "123456";
}
