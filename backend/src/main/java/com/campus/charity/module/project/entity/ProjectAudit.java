package com.campus.charity.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目审核记录（管理员用例：审核众筹项目 include 项目合规审查 / 查看审核意见）。
 */
@Data
@TableName("project_audit")
public class ProjectAudit implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long auditId;
    private Long projectId;
    private Long auditorId;
    private String auditorName;
    /** SUBMIT / APPROVE / REJECT / OFFLINE / COMPLAINT / FINISH */
    private String auditType;
    private String result;
    private String opinion;
    /** 合规审查项 JSON（真实性 / 合规性 / 材料完整性 / 预算合理性 / 帮扶对象真实性） */
    private String complianceItems;
    private LocalDateTime auditTime;
    private Integer notifyStatus;
    private LocalDateTime createTime;
}
