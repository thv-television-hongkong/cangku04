package com.campus.charity.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统操作日志（管理员用例：查看系统日志 include 操作审计追踪）。
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long logId;
    private Long userId;
    private String account;
    private String realName;
    private String role;
    private String module;
    private String operation;
    private String method;
    private String requestUri;
    private String requestMethod;
    private String requestParams;
    private String ip;
    private String userAgent;
    private String resultStatus;
    private String errorMsg;
    private Long durationMs;
    private LocalDateTime createTime;
}
