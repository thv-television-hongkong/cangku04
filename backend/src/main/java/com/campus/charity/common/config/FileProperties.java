package com.campus.charity.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置：原始文件存业务服务器，仅哈希上链。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.file")
public class FileProperties {

    /** 本地存储根目录 */
    private String uploadPath = "./upload";
    /** 下载地址前缀 */
    private String urlPrefix = "/api/file/download/";
    /** 允许上传的扩展名 */
    private String allowedExtensions = "jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,txt,zip";
    /** 单文件最大体积(MB) */
    private Integer maxSizeMb = 50;

    public boolean isAllowed(String extension) {
        if (extension == null) {
            return false;
        }
        String lower = extension.toLowerCase();
        for (String item : allowedExtensions.split(",")) {
            if (item.trim().equalsIgnoreCase(lower)) {
                return true;
            }
        }
        return false;
    }
}
