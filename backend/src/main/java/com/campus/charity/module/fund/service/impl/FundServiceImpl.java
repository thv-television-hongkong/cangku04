package com.campus.charity.module.fund.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.charity.common.annotation.OpLog;
import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.constant.BizTypeConst;
import com.campus.charity.common.constant.CommonConst;
import com.campus.charity.common.constant.ProjectStatusConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.HashUtil;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.fabric.dto.ChainResultDTO;
import com.campus.charity.fabric.dto.EvidencePayload;
import com.campus.charity.module.audit.service.WarningService;
import com.campus.charity.module.donation.mapper.DonationMapper;
import com.campus.charity.module.evidence.service.EvidenceChainService;
import com.campus.charity.module.file.service.FileService;
import com.campus.charity.module.fund.dto.FundDtos;
import com.campus.charity.module.fund.entity.FundUsage;
import com.campus.charity.module.fund.entity.FundWithdrawal;
import com.campus.charity.module.fund.mapper.FundUsageMapper;
import com.campus.charity.module.fund.mapper.FundWithdrawalMapper;
import com.campus.charity.module.fund.service.FundService;
import com.campus.charity.module.notice.service.NotificationService;
import com.campus.charity.module.project.entity.Project;
import com.campus.charity.module.project.mapper.ProjectMapper;
import com.campus.charity.module.user.entity.SysUser;
import com.campus.charity.module.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import com.campus.charity.security.BusinessAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资金服务实现。
 *
 * <p>原始发票 / 收据存业务服务器，仅其 SHA-256 哈希上链存证；资金提取记录同样哈希上链，
 * 形成“捐赠 -> 提取 -> 使用凭证”的完整可追溯资金链路。</p>
 */
@Slf4j
@Service
public class FundServiceImpl implements FundService {

    private final FundUsageMapper usageMapper;
    private final FundWithdrawalMapper withdrawalMapper;
    private final ProjectMapper projectMapper;
    private final DonationMapper donationMapper;
    private final UserService userService;
    private final FileService fileService;
    private final EvidenceChainService chainService;
    private final WarningService warningService;
    private final NotificationService notificationService;
    private final BusinessAccess access;

    public FundServiceImpl(FundUsageMapper usageMapper,
                           FundWithdrawalMapper withdrawalMapper,
                           ProjectMapper projectMapper,
                           DonationMapper donationMapper,
                           UserService userService,
                           FileService fileService,
                           EvidenceChainService chainService,
                           WarningService warningService,
                           NotificationService notificationService, BusinessAccess access) {
        this.usageMapper = usageMapper;
        this.withdrawalMapper = withdrawalMapper;
        this.projectMapper = projectMapper;
        this.donationMapper = donationMapper;
        this.userService = userService;
        this.fileService = fileService;
        this.chainService = chainService;
        this.warningService = warningService;
        this.notificationService = notificationService;this.access=access;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "资金管理", value = "上传资金使用凭证并上链存证")
    public Long uploadUsage(FundDtos.UsageForm form) {
        Project project = getOwnedProject(form.getProjectId());
        if (StrUtil.isBlank(form.getFileHash()) || !HashUtil.isSha256(form.getFileHash())) {
            throw new BizException(ResultCode.FILE_ERROR, "请先上传资金使用凭证文件（发票 / 收据）");
        }
        if (form.getAmount() == null || form.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "资金使用金额必须大于 0");
        }

        if(form.getFileId()==null || !SecurityUtil.getUserId().equals(fileService.getById(form.getFileId()).getUploaderId()) || !form.getFileHash().equals(fileService.getById(form.getFileId()).getFileHash())) throw new BizException(ResultCode.FORBIDDEN,"请使用本人上传且哈希一致的凭证原件");
        FundUsage usage = new FundUsage();
        usage.setUsageNo("FUN" + System.currentTimeMillis());
        usage.setProjectId(project.getProjectId());
        usage.setClubId(SecurityUtil.getUserId());
        usage.setUsageDesc(form.getUsageDesc());
        usage.setAmount(form.getAmount());
        usage.setUseTime(form.getUseTime() == null ? LocalDateTime.now() : form.getUseTime());
        usage.setFileId(form.getFileId());
        usage.setFileName(form.getFileName());
        usage.setFileUrl(form.getFileUrl());
        usage.setFileHash(form.getFileHash());
        usage.setUploadTime(LocalDateTime.now());
        usage.setAuditStatus(CommonConst.AUDIT_PENDING);
        usage.setDeleted(0);
        usage.setCreateTime(LocalDateTime.now());
        usage.setUpdateTime(LocalDateTime.now());
        usageMapper.insert(usage);

        // 资金使用凭证哈希上链存证
        String dataHash = HashUtil.fundUsageHash(usage.getUsageId(), usage.getProjectId(),
                usage.getAmount(), usage.getUsageDesc(), usage.getFileHash());
        SysUser operator = userService.getById(SecurityUtil.getUserId());
        ChainResultDTO chainResult = chainService.submit(EvidencePayload.builder()
                .bizType(BizTypeConst.FUND_USAGE)
                .bizId(String.valueOf(usage.getUsageId()))
                .projectId(String.valueOf(usage.getProjectId()))
                .dataHash(dataHash)
                .fileHash(usage.getFileHash())
                .amount(usage.getAmount().toPlainString())
                .status("PENDING_AUDIT")
                .operator(operator.getAccount())
                .operatorRole(operator.getRole())
                .operatorId(operator.getUserId())
                .operatorName(operator.getRealName())
                .fileId(usage.getFileId())
                .functionName("CreateFundUsageEvidence")
                .build());
        usage.setDataHash(dataHash);
        usage.setTxId(chainResult.getTxId());
        usage.setBlockHeight(chainResult.getBlockHeight());
        usageMapper.updateById(usage);
        if (usage.getFileId() != null) {
            fileService.markOnChain(usage.getFileId(), chainResult.getTxId(), chainResult.getBlockHeight());
        }

        notificationService.sendToRole(com.campus.charity.common.constant.RoleConst.ADMIN, "新的资金使用凭证待审核",
                "项目《" + project.getTitle() + "》上传了资金使用凭证，金额 " + usage.getAmount().toPlainString() + " 元。",
                "FUND", String.valueOf(usage.getUsageId()));
        log.info("资金使用凭证上链成功 usageId={} txId={} blockHeight={}",
                usage.getUsageId(), usage.getTxId(), usage.getBlockHeight());
        return usage.getUsageId();
    }

    @Override
    public PageResult<Map<String, Object>> pageUsages(Long projectId, Integer auditStatus, long current, long size) {
        LambdaQueryWrapper<FundUsage> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            access.requireProject(projectId);
            wrapper.eq(FundUsage::getProjectId, projectId);
        }
        if (auditStatus != null) {
            wrapper.eq(FundUsage::getAuditStatus, auditStatus);
        }
        wrapper.orderByDesc(FundUsage::getUsageId);
        IPage<FundUsage> page = usageMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page, this::toUsageMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "资金管理", value = "审核资金使用凭证")
    public void auditUsage(FundDtos.UsageAuditForm form) {
        FundUsage usage = getUsage(form.getUsageId());
        usage.setAuditStatus(form.getAuditStatus());
        usage.setAuditOpinion(form.getAuditOpinion());
        usage.setAuditUserId(SecurityUtil.getUserId());
        usage.setAuditTime(LocalDateTime.now());
        usage.setUpdateTime(LocalDateTime.now());
        usageMapper.updateById(usage);

        // 审核结果同步上链存证（状态变更）
        String dataHash = HashUtil.sha256Hex("FUND_USAGE_AUDIT|" + usage.getUsageId() + "|"
                + form.getAuditStatus() + "|" + CommonConst.AUDIT_PASSED);
        SysUser operator = userService.getById(SecurityUtil.getUserId());
        ChainResultDTO chainResult = chainService.submit(EvidencePayload.builder()
                .bizType(BizTypeConst.FUND_USAGE)
                .bizId(String.valueOf(usage.getUsageId()))
                .projectId(String.valueOf(usage.getProjectId()))
                .dataHash(dataHash)
                .fileHash(usage.getFileHash())
                .amount(usage.getAmount().toPlainString())
                .status(form.getAuditStatus() != null && form.getAuditStatus() == CommonConst.AUDIT_PASSED
                        ? "APPROVED" : "REJECTED")
                .operator(operator.getAccount())
                .operatorRole(operator.getRole())
                .operatorId(operator.getUserId())
                .operatorName(operator.getRealName())
                .fileId(usage.getFileId())
                .functionName("CreateFundUsageEvidence")
                .build());
        usage.setTxId(chainResult.getTxId());
        usage.setBlockHeight(chainResult.getBlockHeight());
        usageMapper.updateById(usage);

        Project project = projectMapper.selectById(usage.getProjectId());
        if (project != null) {
            notificationService.send(usage.getClubId(), "资金使用凭证审核结果",
                    "您的资金使用凭证（" + usage.getUsageNo() + "）审核结果："
                            + (form.getAuditStatus() != null && form.getAuditStatus() == CommonConst.AUDIT_PASSED ? "已通过" : "已驳回")
                            + "，审核意见：" + StrUtil.blankToDefault(form.getAuditOpinion(), "无"),
                    "FUND", String.valueOf(usage.getUsageId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "资金管理", value = "申请资金提取并上链存证")
    public Long applyWithdrawal(FundDtos.WithdrawalForm form) {
        Project project = getOwnedProject(form.getProjectId());
        if (form.getAmount() == null || form.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "提取金额必须大于 0");
        }
        if(project.getCurrentAmount().compareTo(project.getTargetAmount())<0) throw new BizException(ResultCode.BAD_REQUEST,"达到筹款目标后才可申请资金提取");
        BigDecimal raised = donationMapper.sumByProject(project.getProjectId());
        BigDecimal withdrawn = withdrawalMapper.sumWithdrawnByProject(project.getProjectId());
        BigDecimal available = (raised == null ? BigDecimal.ZERO : raised)
                .subtract(withdrawn == null ? BigDecimal.ZERO : withdrawn);
        if (form.getAmount().compareTo(available) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "提取金额超过可提取余额，当前可提取 " + available.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + " 元");
        }

        FundWithdrawal withdrawal = new FundWithdrawal();
        withdrawal.setWithdrawalNo("WIT" + System.currentTimeMillis());
        withdrawal.setProjectId(project.getProjectId());
        withdrawal.setClubId(SecurityUtil.getUserId());
        withdrawal.setAmount(form.getAmount());
        withdrawal.setReason(form.getReason());
        withdrawal.setStage(form.getStage());
        withdrawal.setApplyTime(LocalDateTime.now());
        withdrawal.setStatus(0);
        withdrawal.setDeleted(0);
        withdrawal.setCreateTime(LocalDateTime.now());
        withdrawal.setUpdateTime(LocalDateTime.now());
        withdrawalMapper.insert(withdrawal);

        // 提取记录哈希上链存证
        String dataHash = HashUtil.withdrawalHash(withdrawal.getWithdrawalId(), withdrawal.getProjectId(),
                withdrawal.getAmount(), withdrawal.getApplyTime());
        SysUser operator = userService.getById(SecurityUtil.getUserId());
        ChainResultDTO chainResult = chainService.submit(EvidencePayload.builder()
                .bizType(BizTypeConst.WITHDRAWAL)
                .bizId(String.valueOf(withdrawal.getWithdrawalId()))
                .projectId(String.valueOf(withdrawal.getProjectId()))
                .dataHash(dataHash)
                .amount(withdrawal.getAmount().toPlainString())
                .status("APPLIED")
                .stage(form.getStage())
                .operator(operator.getAccount())
                .operatorRole(operator.getRole())
                .operatorId(operator.getUserId())
                .operatorName(operator.getRealName())
                .functionName("CreateWithdrawalEvidence")
                .build());
        withdrawal.setDataHash(dataHash);
        withdrawal.setTxId(chainResult.getTxId());
        withdrawal.setBlockHeight(chainResult.getBlockHeight());
        withdrawalMapper.updateById(withdrawal);

        try {
            warningService.evaluateWithdrawal(withdrawal.getProjectId(), withdrawal.getAmount());
        } catch (Exception e) {
            log.warn("资金提取预警评估失败：{}", e.getMessage());
        }

        notificationService.sendToRole(com.campus.charity.common.constant.RoleConst.ADMIN, "新的资金提取申请",
                "项目《" + project.getTitle() + "》申请提取资金 " + withdrawal.getAmount().toPlainString() + " 元。",
                "FUND", String.valueOf(withdrawal.getWithdrawalId()));
        return withdrawal.getWithdrawalId();
    }

    @Override
    public PageResult<FundWithdrawal> pageWithdrawals(Long projectId, Integer status, long current, long size) {
        LambdaQueryWrapper<FundWithdrawal> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            access.requireProject(projectId);
            wrapper.eq(FundWithdrawal::getProjectId, projectId);
        }
        if (SecurityUtil.isClub()) {
            wrapper.eq(FundWithdrawal::getClubId, SecurityUtil.getUserId());
        }
        if (status != null) {
            wrapper.eq(FundWithdrawal::getStatus, status);
        }
        wrapper.orderByDesc(FundWithdrawal::getWithdrawalId);
        IPage<FundWithdrawal> page = withdrawalMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "资金管理", value = "审核/发放资金提取")
    public void auditWithdrawal(FundDtos.WithdrawalAuditForm form) {
        FundWithdrawal withdrawal = getWithdrawal(form.getWithdrawalId());
        withdrawal.setStatus(form.getStatus());
        withdrawal.setAuditOpinion(form.getAuditOpinion());
        withdrawal.setAuditUserId(SecurityUtil.getUserId());
        withdrawal.setAuditTime(LocalDateTime.now());
        if (form.getStatus() != null && form.getStatus() == 3) {
            withdrawal.setPayTime(LocalDateTime.now());
        }
        withdrawal.setUpdateTime(LocalDateTime.now());

        // 审批结果上链存证（状态变更）
        String dataHash = HashUtil.sha256Hex("WITHDRAWAL_AUDIT|" + withdrawal.getWithdrawalId() + "|"
                + form.getStatus() + "|" + HashUtil.TIME_FORMAT.format(LocalDateTime.now()));
        SysUser operator = userService.getById(SecurityUtil.getUserId());
        ChainResultDTO chainResult = chainService.submit(EvidencePayload.builder()
                .bizType(BizTypeConst.WITHDRAWAL)
                .bizId(String.valueOf(withdrawal.getWithdrawalId()))
                .projectId(String.valueOf(withdrawal.getProjectId()))
                .dataHash(dataHash)
                .amount(withdrawal.getAmount().toPlainString())
                .status(statusLabel(form.getStatus()))
                .operator(operator.getAccount())
                .operatorRole(operator.getRole())
                .operatorId(operator.getUserId())
                .operatorName(operator.getRealName())
                .functionName("CreateWithdrawalEvidence")
                .build());
        withdrawal.setTxId(chainResult.getTxId());
        withdrawal.setBlockHeight(chainResult.getBlockHeight());
        withdrawalMapper.updateById(withdrawal);

        notificationService.send(withdrawal.getClubId(), "资金提取申请处理结果",
                "您的资金提取申请（" + withdrawal.getWithdrawalNo() + "）当前状态：" + statusLabel(form.getStatus())
                        + "，审核意见：" + StrUtil.blankToDefault(form.getAuditOpinion(), "无"),
                "FUND", String.valueOf(withdrawal.getWithdrawalId()));
    }

    @Override
    public Map<String, Object> fundOverview(Long projectId) {
        access.requireProject(projectId);
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        }
        BigDecimal raised = donationMapper.sumByProject(projectId);
        BigDecimal withdrawn = withdrawalMapper.sumWithdrawnByProject(projectId);
        BigDecimal used = usageMapper.sumApprovedByProject(projectId);
        List<FundUsage> usages = usageMapper.selectList(new LambdaQueryWrapper<FundUsage>()
                .eq(FundUsage::getProjectId, projectId).orderByDesc(FundUsage::getUsageId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("projectTitle", project.getTitle());
        result.put("targetAmount", project.getTargetAmount());
        result.put("raisedAmount", raised);
        result.put("withdrawnAmount", withdrawn);
        result.put("usedAmount", used);
        result.put("balanceAmount", (raised == null ? BigDecimal.ZERO : raised)
                .subtract(withdrawn == null ? BigDecimal.ZERO : withdrawn));
        result.put("usageList", usages.stream().map(this::toUsageMap).collect(Collectors.toList()));
        return result;
    }

    @Override
    public FundUsage getUsage(Long usageId) {
        FundUsage usage = usageMapper.selectById(usageId);
        if (usage == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资金使用凭证不存在");
        }
        access.requireProject(usage.getProjectId());
        return usage;
    }

    @Override
    public FundWithdrawal getWithdrawal(Long withdrawalId) {
        FundWithdrawal withdrawal = withdrawalMapper.selectById(withdrawalId);
        if (withdrawal == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资金提取记录不存在");
        }
        access.requireProject(withdrawal.getProjectId());
        return withdrawal;
    }

    // ---------------------------------------------------------------------------------

    private Project getOwnedProject(Long projectId) {
        if (projectId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择项目");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        }
        if (SecurityUtil.isClub() && !project.getClubId().equals(SecurityUtil.getUserId())) {
            throw new BizException(ResultCode.PROJECT_NOT_OWNED);
        }
        if (ProjectStatusConst.OFFLINE.equals(project.getStatus())) {
            throw new BizException(ResultCode.PROJECT_STATUS_ERROR, "项目已下架，不能提交资金材料");
        }
        return project;
    }

    private Map<String, Object> toUsageMap(FundUsage usage) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("usageId", usage.getUsageId());
        map.put("usageNo", usage.getUsageNo());
        map.put("projectId", usage.getProjectId());
        map.put("usageDesc", usage.getUsageDesc());
        map.put("amount", usage.getAmount());
        map.put("useTime", usage.getUseTime());
        map.put("fileId", usage.getFileId());
        map.put("fileName", usage.getFileName());
        map.put("fileUrl", usage.getFileUrl());
        map.put("fileHash", usage.getFileHash());
        map.put("uploadTime", usage.getUploadTime());
        map.put("auditStatus", usage.getAuditStatus());
        map.put("auditOpinion", usage.getAuditOpinion());
        map.put("auditTime", usage.getAuditTime());
        map.put("dataHash", usage.getDataHash());
        map.put("txId", usage.getTxId());
        map.put("blockHeight", usage.getBlockHeight());
        return map;
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return "UNKNOWN";
        }
        switch (status) {
            case 0:
                return "PENDING";
            case 1:
                return "APPROVED";
            case 2:
                return "REJECTED";
            case 3:
                return "PAID";
            default:
                return "UNKNOWN";
        }
    }
}
