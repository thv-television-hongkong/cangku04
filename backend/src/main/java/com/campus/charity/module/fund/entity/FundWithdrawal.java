package com.campus.charity.module.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金提取申请（受助者用例：申请资金提取 extends 提交区块链存证）。
 */
@Data
@TableName("fund_withdrawal")
public class FundWithdrawal implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long withdrawalId;
    private String withdrawalNo;
    private Long projectId;
    private Long clubId;
    private BigDecimal amount;
    private String reason;
    private String stage;
    private LocalDateTime applyTime;
    /** 0待审核 1已通过 2已驳回 3已发放 */
    private Integer status;
    private Long auditUserId;
    private String auditOpinion;
    private LocalDateTime auditTime;
    private LocalDateTime payTime;
    private String dataHash;
    private String txId;
    private Long blockHeight;
    private Long evidenceId;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
