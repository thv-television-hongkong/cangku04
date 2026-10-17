package com.campus.charity.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审核 / 预警规则（监管方用例：管理审核规则 extends 设置预警规则）。
 */
@Data
@TableName("audit_rule")
public class AuditRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long ruleId;
    private String ruleName;
    /** SINGLE_AMOUNT / FREQUENCY / PROJECT_TARGET / DURATION / DAILY_TOTAL / PROJECT_PROGRESS / OTHER */
    private String ruleType;
    /** DONATION / PROJECT / FUND_USAGE / WITHDRAWAL */
    private String bizType;
    /** > >= < <= = != */
    private String comparison;
    private BigDecimal thresholdValue;
    /** YUAN / COUNT / DAY / PERCENT */
    private String thresholdUnit;
    /** LOW / MID / HIGH */
    private String warningLevel;
    private Integer enabled;
    private String description;
    private Integer hitCount;
    private Long createUserId;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
