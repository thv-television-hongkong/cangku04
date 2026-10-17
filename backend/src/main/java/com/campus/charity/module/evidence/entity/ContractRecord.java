package com.campus.charity.module.evidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能合约部署 / 升级记录（管理员用例：管理智能合约 extends 部署合约升级）。
 */
@Data
@TableName("contract_record")
public class ContractRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long contractId;
    private String chaincodeName;
    private String chaincodeVersion;
    private Long sequenceNo;
    private String channelName;
    /** DEPLOY / UPGRADE */
    private String operationType;
    private String packageId;
    private String endorsementPolicy;
    private String txId;
    private Long blockHeight;
    /** SUCCESS / FAILED */
    private String status;
    private String errorMsg;
    private Long operatorId;
    private String operatorName;
    private String description;
    private LocalDateTime operationTime;
}
