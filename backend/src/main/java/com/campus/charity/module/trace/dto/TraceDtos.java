package com.campus.charity.module.trace.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 溯源查询模块 DTO / VO 集合。
 */
public class TraceDtos {

    /** 多维溯源查询条件：项目ID / 交易ID / 区块高度 / 业务类型+业务ID */
    @Data
    public static class TraceQuery implements Serializable {
        /** PROJECT / DONATION / FUND_USAGE / MATERIAL / WITHDRAWAL / PROJECT_PROGRESS */
        private String bizType;
        private String bizId;
        private String txId;
        private Long blockHeight;
        private Long projectId;
        private String dataHash;
        private String projectNo;
        private String orderNo;
        private String evidenceNo;
    }

    /** 哈希校验请求 */
    @Data
    public static class VerifyForm implements Serializable {
        private String bizType;
        private String bizId;
        /** 待比对的哈希（可直接传文件哈希） */
        private String hash;
        private Long fileId;
    }

    /** 溯源结果 */
    @Data
    public static class TraceResult implements Serializable {
        /** 查询维度：BIZ / TX / BLOCK / PROJECT */
        private String dimension;
        private String message;
        private List<Map<String, Object>> records = new ArrayList<>();
        /** 项目全流程时间线 */
        private List<Map<String, Object>> timeline = new ArrayList<>();
        /** 链式完整性 */
        private Boolean chainIntact;
        /** 链上统计信息 */
        private Map<String, Object> chainInfo;
    }

    /** 哈希校验结果（含链上凭证） */
    @Data
    public static class VerifyResult implements Serializable {
        private boolean matched;
        private String outcome;
        private String message;
        private String bizType;
        private String bizId;
        private String providedHash;
        private String storedHash;
        private String txId;
        private Long blockHeight;
        private Long chainSeq;
        private String chainTime;
        private String chainSource;
        /** 链下原始文件对照信息 */
        private Long fileId;
        private String fileName;
        private String fileUrl;
    }

    private TraceDtos() {
    }
}
