package com.campus.charity.module.audit.service;

import com.campus.charity.common.api.PageResult;
import com.campus.charity.module.audit.entity.AuditReport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计报告服务（监管方用例：生成审计报告 include 导出存证数据）。
 */
public interface AuditReportService {

    /** 生成审计报告（统计链上存证 + 哈希核验 + 报告文件存证） */
    AuditReport generate(String reportType, Long projectId, LocalDateTime startTime, LocalDateTime endTime);

    PageResult<AuditReport> page(Long projectId, String reportType, long current, long size);

    AuditReport getById(Long reportId);

    /** 导出报告明细数据（存证数据导出行） */
    List<Map<String, Object>> exportRows(Long reportId);

    /** 删除审计报告（仅删除记录，链上存证不可删除） */
    void delete(Long reportId);
}
