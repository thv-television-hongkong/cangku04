package com.campus.charity.module.fund.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金模块 DTO 集合：资金使用凭证（ER 实体 5）与资金提取申请。
 */
public class FundDtos {

    /** 上传资金使用凭证（extends 查看存证溯源） */
    @Data
    public static class UsageForm implements Serializable {
        private Long projectId;
        private String usageDesc;
        private BigDecimal amount;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime useTime;
        private Long fileId;
        private String fileName;
        private String fileUrl;
        /** 文件哈希（上链内容） */
        private String fileHash;
    }

    /** 资金凭证审核 */
    @Data
    public static class UsageAuditForm implements Serializable {
        private Long usageId;
        /** 1 通过 / 2 驳回 */
        private Integer auditStatus;
        private String auditOpinion;
    }

    /** 申请资金提取（extends 提交区块链存证） */
    @Data
    public static class WithdrawalForm implements Serializable {
        private Long projectId;
        private BigDecimal amount;
        private String reason;
        private String stage;
    }

    /** 资金提取审核 */
    @Data
    public static class WithdrawalAuditForm implements Serializable {
        private Long withdrawalId;
        /** 1 通过 / 2 驳回 / 3 已发放 */
        private Integer status;
        private String auditOpinion;
    }

    private FundDtos() {
    }
}
