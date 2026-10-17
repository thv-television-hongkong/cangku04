package com.campus.charity.fabric.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 链码返回的存证凭证（对外作为“存证凭证”：交易 ID + 区块高度 + 存证哈希 + 上链时间）。
 */
@Data
public class ChainResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Fabric 交易ID */
    private String txId;
    private Long taskId;
    private String evidenceStatus;
    /** 区块高度 */
    private Long blockHeight;
    /** 链上存证序号 */
    private Long chainSeq;
    /** 上链时间 */
    private String timestamp;
    /** 存证数据哈希 */
    private String dataHash;
    /** 链式哈希（前序哈希串联，防篡改） */
    private String chainHash;
    /** 链上主键 */
    private String evidenceKey;
    /** 存证编号 */
    private String evidenceNo;
    private String bizType;
    private String bizId;
    private String projectId;
    private String status;
    /** 存证来源：FABRIC 真实链 / MOCK 模拟链 */
    private String chainSource;
}
