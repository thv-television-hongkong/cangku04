package com.campus.charity.module.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知（extends 通知相关方 / 审核结果通知）。
 */
@Data
@TableName("notification")
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long noticeId;
    private Long userId;
    private String title;
    private String content;
    /** PROJECT_AUDIT / FUND / COMPLAINT / WARNING / SYSTEM */
    private String noticeType;
    private String bizId;
    private Integer readStatus;
    private LocalDateTime createTime;
}
