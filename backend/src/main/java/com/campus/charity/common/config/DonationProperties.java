package com.campus.charity.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 捐赠业务配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.donation")
public class DonationProperties {

    /** 限定时间内可取消捐赠（分钟），默认 120 分钟 */
    private Integer cancelWindowMinutes = 120;
}
