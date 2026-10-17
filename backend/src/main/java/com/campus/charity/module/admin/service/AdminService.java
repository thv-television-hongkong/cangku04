package com.campus.charity.module.admin.service;

import com.campus.charity.common.api.PageResult;
import com.campus.charity.module.admin.entity.Complaint;
import com.campus.charity.module.admin.entity.Publicity;
import com.campus.charity.module.admin.entity.SysBackupRecord;
import com.campus.charity.module.admin.entity.SysOperationLog;
import com.campus.charity.module.evidence.entity.ContractRecord;

import java.util.List;
import java.util.Map;

/**
 * 后台监管服务：数据统计、系统日志、公益公示、投诉纠纷、数据库备份、智能合约管理。
 */
public interface AdminService {

    /** 运营数据总览 */
    Map<String, Object> dashboard();

    Map<String, Object> statistics();

    /** 操作日志分页 */
    PageResult<SysOperationLog> pageLogs(String module, String keyword, long current, long size);

    /** 操作审计行为统计 */
    List<Map<String, Object>> logStatistics();

    /** 提交投诉（用户） */
    Long submitComplaint(Complaint complaint);

    /** 投诉分页 */
    PageResult<Complaint> pageComplaints(Integer status, Long projectId, long current, long size);
    PageResult<Complaint> pageMyComplaints(long current, long size);

    /** 处理投诉（extends 通知相关方） */
    void handleComplaint(Long complaintId, Integer status, String result);

    /** 公益信息公示分页 */
    PageResult<Publicity> pagePublicity(Integer status, long current, long size);

    /** 发布校内公示 */
    Long publish(Publicity publicity);

    /** 撤回公示 */
    void revokePublicity(Long publicityId);

    /** 数据库备份记录 */
    PageResult<SysBackupRecord> pageBackups(long current, long size);

    /** 手动执行数据库备份 */
    SysBackupRecord runBackup(String backupType);

    /** 智能合约部署 / 升级记录 */
    List<ContractRecord> listContracts();

    /** 记录合约部署 / 升级并上链存证 */
    ContractRecord recordContract(ContractRecord record);

    /** 合约 / 区块链节点运行状态 */
    Map<String, Object> chainStatus();
}
