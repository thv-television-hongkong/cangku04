package com.campus.charity.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审计报告（监管方用例：生成审计报告 include 导出存证数据）。
 */
@Data
@TableName("audit_report")
public class AuditReport implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long reportId;
    private String reportNo;
    private String title;
    /** PROJECT / FUND / EVIDENCE / COMPREHENSIVE */
    private String reportType;
    private Long projectId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String summary;
    private BigDecimal totalAmount;
    private Integer donationCount;
    private Integer evidenceCount;
    private Integer warningCount;
    /** ALL_MATCH / PARTIAL_MISMATCH */
    private String verifyResult;
    private Long fileId;
    private String fileUrl;
    private String fileHash;
    private String txId;
    private Long blockHeight;
    private Long generateUserId;
    private String generateUserName;
    private LocalDateTime generateTime;
}
