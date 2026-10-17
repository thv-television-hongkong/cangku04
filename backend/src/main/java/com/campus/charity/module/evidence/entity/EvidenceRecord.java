package com.campus.charity.module.evidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 存证凭证（ER 实体 4）。
 *
 * <p>凭证ID / 交易ID / 区块高度 / 上链时间 / 存证数据哈希 / 关联业务ID。</p>
 */
@Data
@TableName("evidence_record")
public class EvidenceRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long evidenceId;
    private String evidenceNo;
    /** Fabric 交易ID（存证凭证核心字段） */
    private String txId;
    /** 区块高度（后端从 CommitStatus 回填） */
    private Long blockHeight;
    private LocalDateTime chainTime;
    /** 存证数据哈希（SHA-256，链上唯一内容） */
    private String dataHash;
    /** PROJECT / DONATION / FUND_USAGE / MATERIAL / WITHDRAWAL / PROJECT_PROGRESS / AUDIT_REPORT / PROJECT_STATUS */
    private String bizType;
    private String bizId;
    private Long projectId;
    /** 链下原始文件ID（原始文件存业务服务器） */
    private Long fileId;
    private String channelName;
    private String chaincodeName;
    private String functionName;
    private Long operatorId;
    private String operatorName;
    /** SUCCESS / FAILED / PENDING */
    private String status;
    /** 上链业务摘要 JSON（仅哈希与非敏感字段） */
    private String payload;
    private String errorMsg;
    /** FABRIC 真实链 / MOCK 模拟链 */
    private String chainSource;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
