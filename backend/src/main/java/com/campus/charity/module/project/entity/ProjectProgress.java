package com.campus.charity.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目进度（受助者用例：编辑众筹项目 includes 更新项目进度）。
 */
@Data
@TableName("project_progress")
public class ProjectProgress implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long progressId;
    private Long projectId;
    private String title;
    private String content;
    private Integer progressPercent;
    private Long fileId;
    private String fileHash;
    private Long createUserId;
    private String dataHash;
    private String txId;
    private Long blockHeight;
    private Long evidenceId;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
