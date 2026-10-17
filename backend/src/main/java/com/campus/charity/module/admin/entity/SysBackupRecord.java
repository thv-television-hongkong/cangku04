package com.campus.charity.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据库备份记录（可靠性需求：MySQL 业务数据定期备份）。
 */
@Data
@TableName("sys_backup_record")
public class SysBackupRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long backupId;
    private String backupName;
    private String backupPath;
    private Long backupSize;
    /** AUTO / MANUAL */
    private String backupType;
    private String status;
    private String errorMsg;
    private Long durationMs;
    private Long triggerUserId;
    private LocalDateTime createTime;
}
