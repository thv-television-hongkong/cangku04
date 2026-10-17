package com.campus.charity.fabric.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 链上存证记录。
 */
@Data
public class EvidenceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String key;
    private String eventKey;
    private String bizType;
    private String bizId;
    private String projectId;
    private String dataHash;
    private String fileHash;
    private String amount;
    private String amountHash;
    private String donorHash;
    private String operator;
    private String operatorRole;
    private String status;
    private String remark;
    private String txId;
    private Long chainSeq;
    private Long blockHeight;
    private String timestamp;
    private String prevChainHash;
    private String chainHash;
    private String version;
}
