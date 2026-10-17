package com.campus.charity.module.audit.service;

import com.campus.charity.common.api.PageResult;
import com.campus.charity.module.audit.entity.AuditRule;
import com.campus.charity.module.audit.entity.WarningRecord;
import com.campus.charity.module.donation.entity.Donation;
import com.campus.charity.module.project.entity.Project;

import java.util.List;
import java.util.Map;

/**
 * 预警服务（监管方用例：管理审核规则 extends 设置预警规则）。
 */
public interface WarningService {

    /** 捐赠发生后评估预警规则（单笔金额 / 单日累计 / 频次） */
    void evaluateDonation(Donation donation);

    /** 项目发布 / 上线后评估预警规则（目标金额 / 周期） */
    void evaluateProject(Project project);

    /** 资金提取后评估预警规则（资金使用进度） */
    void evaluateWithdrawal(Long projectId, java.math.BigDecimal amount);

    /** 规则列表 */
    List<AuditRule> listRules(Boolean enabled);

    AuditRule saveRule(AuditRule rule);

    void updateRule(AuditRule rule);

    void toggleRule(Long ruleId, boolean enabled);

    void deleteRule(Long ruleId);

    /** 预警记录分页 */
    PageResult<WarningRecord> pageWarnings(Integer handleStatus, String level, long current, long size);

    /** 处理预警 */
    void handleWarning(Long warningId, Integer handleStatus, String opinion);

    List<Map<String, Object>> countByLevel();

    long countUnhandled();
}
