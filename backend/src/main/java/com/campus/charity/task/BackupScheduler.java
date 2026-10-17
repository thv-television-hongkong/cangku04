package com.campus.charity.task;

import com.campus.charity.common.config.BackupProperties;
import com.campus.charity.module.admin.entity.SysBackupRecord;
import com.campus.charity.module.admin.service.AdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据库备份定时任务（可靠性需求：MySQL 业务数据定期备份）。
 */
@Slf4j
@Component
public class BackupScheduler {

    private final AdminService adminService;
    private final BackupProperties backupProperties;

    public BackupScheduler(AdminService adminService, BackupProperties backupProperties) {
        this.adminService = adminService;
        this.backupProperties = backupProperties;
    }

    /** 按配置的 cron 执行（默认每日 02:00） */
    @Scheduled(cron = "${campus.backup.cron:0 0 2 * * ?}")
    public void autoBackup() {
        if (!backupProperties.isEnabled()) {
            return;
        }
        SysBackupRecord record = adminService.runBackup("AUTO");
        log.info("自动备份任务完成：{} ({})", record.getBackupName(), record.getStatus());
    }
}
