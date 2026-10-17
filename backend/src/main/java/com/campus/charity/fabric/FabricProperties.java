package com.campus.charity.fabric;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Hyperledger Fabric 连接配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fabric")
public class FabricProperties {

    /** true 连接真实联盟链；false 使用内置模拟链（开发联调） */
    private boolean enabled = false;
    private String mspId = "Org1MSP";
    private String channelName = "charitychannel";
    private String chaincodeName = "charity-cc";
    private String connectionProfile = "../fabric-network/connection-org1.json";
    private String walletPath = "../fabric-network/wallet";
    private String identityLabel = "admin";
    private String certPath;
    private String keyPath;
    private int evaluateTimeoutSeconds = 10;
    private int endorseTimeoutSeconds = 30;
    private int submitTimeoutSeconds = 20;
    private int commitStatusTimeoutSeconds = 120;
}
