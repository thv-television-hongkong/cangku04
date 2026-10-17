package com.campus.charity.module.evidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 链上存证汇总快照：业务库损坏时依旧可核验（可靠性需求）。
 */
@Data
@TableName("chain_evidence_snapshot")
public class ChainEvidenceSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long snapshotId;
    private String txId;
    private String eventKey;
    private Long blockHeight;
    private String bizType;
    private String bizId;
    private Long projectId;
    private String dataHash;
    private LocalDateTime chainTime;
    private String rawPayload;
    private LocalDateTime syncTime;
}
