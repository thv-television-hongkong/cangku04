package com.campus.charity.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投诉与纠纷（管理员用例：处理投诉与纠纷 extends 通知相关方）。
 */
@Data
@TableName("complaint")
public class Complaint implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long complaintId;
    private String complaintNo;
    private Long projectId;
    private Long userId;
    /** FUND_USE / FAKE_PROJECT / PRIVACY / DELAY / OTHER */
    private String complaintType;
    private String title;
    private String content;
    private Long evidenceFileId;
    /** 0待处理 1处理中 2已解决 3已驳回 */
    private Integer status;
    private Long handlerId;
    private String handleResult;
    private LocalDateTime handleTime;
    private Integer notifyStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
