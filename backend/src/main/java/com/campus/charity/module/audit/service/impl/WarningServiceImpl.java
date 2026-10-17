package com.campus.charity.module.audit.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.constant.BizTypeConst;
import com.campus.charity.common.constant.RoleConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.module.audit.entity.AuditRule;
import com.campus.charity.module.audit.entity.WarningRecord;
import com.campus.charity.module.audit.mapper.AuditRuleMapper;
import com.campus.charity.module.audit.mapper.WarningRecordMapper;
import com.campus.charity.module.audit.service.WarningService;
import com.campus.charity.module.donation.entity.Donation;
import com.campus.charity.module.donation.mapper.DonationMapper;
import com.campus.charity.module.fund.mapper.FundWithdrawalMapper;
import com.campus.charity.module.notice.service.NotificationService;
import com.campus.charity.module.project.entity.Project;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预警服务实现：根据监管方配置的规则自动检测异常交易。
 */
@Slf4j
@Service
public class WarningServiceImpl implements WarningService {

    private final AuditRuleMapper ruleMapper;
    private final WarningRecordMapper warningMapper;
    private final DonationMapper donationMapper;
    private final FundWithdrawalMapper withdrawalMapper;
    private final NotificationService notificationService;

    public WarningServiceImpl(AuditRuleMapper ruleMapper,
                              WarningRecordMapper warningMapper,
                              DonationMapper donationMapper,
                              FundWithdrawalMapper withdrawalMapper,
                              NotificationService notificationService) {
        this.ruleMapper = ruleMapper;
        this.warningMapper = warningMapper;
        this.donationMapper = donationMapper;
        this.withdrawalMapper = withdrawalMapper;
        this.notificationService = notificationService;
    }

    @Override
    public void evaluateDonation(Donation donation) {
        List<AuditRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<AuditRule>()
                .eq(AuditRule::getEnabled, 1)
                .eq(AuditRule::getBizType, BizTypeConst.DONATION));
        for (AuditRule rule : rules) {
            BigDecimal value = null;
            if ("SINGLE_AMOUNT".equals(rule.getRuleType())) {
                value = donation.getAmount();
            } else if ("DAILY_TOTAL".equals(rule.getRuleType())) {
                value = donationMapper.sumDailyByDonor(donation.getDonorId(), donation.getDonateTime());
            } else if ("FREQUENCY".equals(rule.getRuleType())) {
                long count = donationMapper.countByDonorSince(donation.getDonorId(),
                        LocalDateTime.now().minusHours(1));
                value = BigDecimal.valueOf(count);
            }
            if (value != null && matches(value, rule.getComparison(), rule.getThresholdValue())) {
                createWarning(rule, value, BizTypeConst.DONATION, String.valueOf(donation.getDonationId()),
                        donation.getProjectId(), donation.getDonorId(),
                        String.format("捐赠异常：%s（当前值 %s%s，阈值 %s%s）",
                                rule.getRuleName(), value.toPlainString(), unitLabel(rule.getThresholdUnit()),
                                rule.getThresholdValue().toPlainString(), unitLabel(rule.getThresholdUnit())));
            }
        }
    }

    @Override
    public void evaluateProject(Project project) {
        List<AuditRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<AuditRule>()
                .eq(AuditRule::getEnabled, 1)
                .eq(AuditRule::getBizType, BizTypeConst.PROJECT));
        for (AuditRule rule : rules) {
            BigDecimal value = null;
            if ("PROJECT_TARGET".equals(rule.getRuleType())) {
                value = project.getTargetAmount();
            } else if ("DURATION".equals(rule.getRuleType()) && project.getStartTime() != null && project.getEndTime() != null) {
                value = BigDecimal.valueOf(Duration.between(project.getStartTime(), project.getEndTime()).toDays());
            }
            if (value != null && matches(value, rule.getComparison(), rule.getThresholdValue())) {
                createWarning(rule, value, BizTypeConst.PROJECT, String.valueOf(project.getProjectId()),
                        project.getProjectId(), project.getClubId(),
                        String.format("项目异常：%s（当前值 %s%s）", rule.getRuleName(), value.toPlainString(),
                                unitLabel(rule.getThresholdUnit())));
            }
        }
    }

    @Override
    public void evaluateWithdrawal(Long projectId, BigDecimal amount) {
        List<AuditRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<AuditRule>()
                .eq(AuditRule::getEnabled, 1)
                .eq(AuditRule::getBizType, BizTypeConst.WITHDRAWAL));
        for (AuditRule rule : rules) {
            BigDecimal value = null;
            if ("PROJECT_PROGRESS".equals(rule.getRuleType())) {
                BigDecimal withdrawn = withdrawalMapper.sumWithdrawnByProject(projectId);
                BigDecimal raised = donationMapper.sumByProject(projectId);
                if (raised != null && raised.compareTo(BigDecimal.ZERO) > 0) {
                    value = withdrawn.multiply(BigDecimal.valueOf(100))
                            .divide(raised, 2, java.math.RoundingMode.HALF_UP);
                }
            } else if ("SINGLE_AMOUNT".equals(rule.getRuleType())) {
                value = amount;
            }
            if (value != null && matches(value, rule.getComparison(), rule.getThresholdValue())) {
                createWarning(rule, value, BizTypeConst.WITHDRAWAL, String.valueOf(projectId),
                        projectId, null,
                        String.format("资金提取异常：%s（当前值 %s%s）", rule.getRuleName(),
                                value.toPlainString(), unitLabel(rule.getThresholdUnit())));
            }
        }
    }

    @Override
    public List<AuditRule> listRules(Boolean enabled) {
        LambdaQueryWrapper<AuditRule> wrapper = new LambdaQueryWrapper<>();
        if (enabled != null) {
            wrapper.eq(AuditRule::getEnabled, enabled ? 1 : 0);
        }
        wrapper.orderByAsc(AuditRule::getRuleId);
        return ruleMapper.selectList(wrapper);
    }

    @Override
    public AuditRule saveRule(AuditRule rule) {
        rule.setRuleId(null);
        rule.setEnabled(rule.getEnabled() == null ? 1 : rule.getEnabled());
        rule.setHitCount(0);
        rule.setCreateUserId(SecurityUtil.getUserIdOrNull());
        rule.setDeleted(0);
        rule.setCreateTime(LocalDateTime.now());
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.insert(rule);
        return rule;
    }

    @Override
    public void updateRule(AuditRule rule) {
        if (rule.getRuleId() == null) {
            throw new BizException(ResultCode.RULE_NOT_FOUND);
        }
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
    }

    @Override
    public void toggleRule(Long ruleId, boolean enabled) {
        AuditRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BizException(ResultCode.RULE_NOT_FOUND);
        }
        rule.setEnabled(enabled ? 1 : 0);
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
    }

    @Override
    public void deleteRule(Long ruleId) {
        ruleMapper.deleteById(ruleId);
    }

    @Override
    public PageResult<WarningRecord> pageWarnings(Integer handleStatus, String level, long current, long size) {
        LambdaQueryWrapper<WarningRecord> wrapper = new LambdaQueryWrapper<>();
        if (handleStatus != null) {
            wrapper.eq(WarningRecord::getHandleStatus, handleStatus);
        }
        if (StrUtil.isNotBlank(level)) {
            wrapper.eq(WarningRecord::getWarningLevel, level);
        }
        wrapper.orderByDesc(WarningRecord::getWarningId);
        IPage<WarningRecord> page = warningMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page);
    }

    @Override
    public void handleWarning(Long warningId, Integer handleStatus, String opinion) {
        WarningRecord warning = warningMapper.selectById(warningId);
        if (warning == null) {
            throw new BizException(ResultCode.NOT_FOUND, "预警记录不存在");
        }
        warning.setHandleStatus(handleStatus);
        warning.setHandleOpinion(opinion);
        warning.setHandlerId(SecurityUtil.getUserIdOrNull());
        warning.setHandleTime(LocalDateTime.now());
        warningMapper.updateById(warning);
    }

    @Override
    public List<Map<String, Object>> countByLevel() {
        return warningMapper.countByLevel();
    }

    @Override
    public long countUnhandled() {
        return warningMapper.countUnhandled();
    }

    // ---------------------------------------------------------------------------------

    private boolean matches(BigDecimal value, String comparison, BigDecimal threshold) {
        if (value == null || threshold == null) {
            return false;
        }
        switch (StrUtil.blankToDefault(comparison, ">")) {
            case ">":
                return value.compareTo(threshold) > 0;
            case ">=":
                return value.compareTo(threshold) >= 0;
            case "<":
                return value.compareTo(threshold) < 0;
            case "<=":
                return value.compareTo(threshold) <= 0;
            case "=":
                return value.compareTo(threshold) == 0;
            case "!=":
                return value.compareTo(threshold) != 0;
            default:
                return false;
        }
    }

    private void createWarning(AuditRule rule, BigDecimal value, String bizType, String bizId,
                               Long projectId, Long userId, String content) {
        WarningRecord record = new WarningRecord();
        record.setRuleId(rule.getRuleId());
        record.setRuleName(rule.getRuleName());
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setProjectId(projectId);
        record.setUserId(userId);
        record.setWarningLevel(rule.getWarningLevel());
        record.setWarningContent(content);
        record.setWarningValue(value);
        record.setHandleStatus(0);
        record.setCreateTime(LocalDateTime.now());
        warningMapper.insert(record);
        ruleMapper.increaseHitCount(rule.getRuleId());
        log.warn("[异常预警] {}", content);
        notificationService.sendToRole(RoleConst.AUDITOR, "异常交易预警", content, "WARNING", bizId);
        notificationService.sendToRole(RoleConst.ADMIN, "异常交易预警", content, "WARNING", bizId);
    }

    private String unitLabel(String unit) {
        if (unit == null) {
            return "";
        }
        switch (unit) {
            case "YUAN":
                return "元";
            case "COUNT":
                return "次";
            case "DAY":
                return "天";
            case "PERCENT":
                return "%";
            default:
                return unit;
        }
    }

    /** 供页面展示规则类型字典 */
    public Map<String, String> ruleTypeLabels() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("SINGLE_AMOUNT", "单笔金额");
        map.put("FREQUENCY", "交易频次");
        map.put("PROJECT_TARGET", "项目目标金额");
        map.put("DURATION", "项目周期");
        map.put("DAILY_TOTAL", "单日累计");
        map.put("PROJECT_PROGRESS", "资金使用进度");
        map.put("OTHER", "其他规则");
        return map;
    }
}
