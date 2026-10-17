package com.campus.charity.module.file.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件模块 DTO / VO。
 */
public class FileDtos {

    /** 文件上传返回：链下存业务服务器，fileHash 用于上链存证 */
    @Data
    public static class FileVO implements Serializable {
        private Long fileId;
        private String fileNo;
        private String fileName;
        private String fileUrl;
        /** SHA-256 摘要（上链内容） */
        private String fileHash;
        private Long fileSize;
        private String contentType;
        private String bizType;
        private String bizId;
        private Integer onChain;
        private String txId;
        private Long blockHeight;
        private LocalDateTime uploadTime;
    }

    /** 文件哈希核验结果 */
    @Data
    public static class FileVerifyVO implements Serializable {
        private boolean matched;
        private String fileId;
        private String fileName;
        private String storedHash;
        private String uploadedHash;
        private String message;
    }

    private FileDtos() {
    }
}
