package com.campus.charity.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目分享记录（捐赠者用例：查看资金流向 extends 分享项目）。
 */
@Data
@TableName("project_share_log")
public class ProjectShareLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long shareId;
    private Long projectId;
    private Long userId;
    private String channel;
    private LocalDateTime createTime;
}
