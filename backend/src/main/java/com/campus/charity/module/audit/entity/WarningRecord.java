package com.campus.charity.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异常交易预警记录。
 */
@Data
@TableName("warning_record")
public class WarningRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long warningId;
    private Long ruleId;
    private String ruleName;
    private String bizType;
    private String bizId;
    private Long projectId;
    private Long userId;
    private String warningLevel;
    private String warningContent;
    private BigDecimal warningValue;
    /** 0未处理 1已处理 2已忽略 */
    private Integer handleStatus;
    private Long handlerId;
    private String handleOpinion;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
