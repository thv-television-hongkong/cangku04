package com.campus.charity.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 众筹项目（ER 实体 2）。
 *
 * <p>项目ID / 项目标题 / 帮扶对象 / 目标金额 / 当前筹款 / 开始时间 / 截止时间 / 项目状态 / 社团ID / 项目描述。</p>
 */
@Data
@TableName("project")
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long projectId;
    private String projectNo;
    private String title;
    private String category;
    /** 帮扶对象姓名（仅社团与管理员可见，对外一律使用 beneficiaryMasked） */
    private String beneficiary;
    /** 帮扶对象脱敏展示名 */
    private String beneficiaryMasked;
    private String beneficiaryContactMasked;
    private String beneficiaryDesc;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** DRAFT / PENDING / REJECTED / ONLINE / OFFLINE / FINISHED / SETTLED */
    private String status;
    private Long clubId;
    private String description;
    private String fundsPlan;
    private String coverImage;
    private Integer donorCount;
    private Integer viewCount;
    private Integer favoriteCount;
    private Integer shareCount;
    private Integer progressPercent;
    /** 项目摘要哈希（上链内容） */
    private String dataHash;
    private String txId;
    private Long blockHeight;
    private Long evidenceId;
    private Long auditUserId;
    private String auditOpinion;
    private LocalDateTime auditTime;
    private String offlineReason;
    private LocalDateTime finishTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
