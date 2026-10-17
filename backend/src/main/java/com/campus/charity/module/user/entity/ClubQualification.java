package com.campus.charity.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 社团资质审核（用户管理模块：社团账号需要资质审核）。
 */
@Data
@TableName("club_qualification")
public class ClubQualification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long qualificationId;
    private Long userId;
    private String clubName;
    private String clubCode;
    private String college;
    private String advisorName;
    private String advisorPhone;
    private Long fileId;
    private String licenseHash;
    /** 1待审核 2已通过 3已驳回 */
    private Integer status;
    private Long auditUserId;
    private String auditOpinion;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
