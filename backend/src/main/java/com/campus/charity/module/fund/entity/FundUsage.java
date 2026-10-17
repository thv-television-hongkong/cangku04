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
 * 资金使用凭证（ER 实体 5）。
 *
 * <p>凭证编号 / 项目ID / 上传社团ID / 资金使用说明 / 文件哈希 / 上传时间 / 审核状态。</p>
 */
@Data
@TableName("fund_usage")
public class FundUsage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long usageId;
    private String usageNo;
    private Long projectId;
    private Long clubId;
    private String usageDesc;
    private BigDecimal amount;
    private LocalDateTime useTime;
    /** 链下原始文件（发票 / 收据） */
    private Long fileId;
    private String fileName;
    private String fileUrl;
    /** 文件哈希（上链内容） */
    private String fileHash;
    private LocalDateTime uploadTime;
    /** 0待审核 1已通过 2已驳回 */
    private Integer auditStatus;
    private Long auditUserId;
    private String auditOpinion;
    private LocalDateTime auditTime;
    private String dataHash;
    private String txId;
    private Long blockHeight;
    private Long evidenceId;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
