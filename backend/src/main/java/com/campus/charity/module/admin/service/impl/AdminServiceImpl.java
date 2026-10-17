package com.campus.charity.module.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.config.BackupProperties;
import com.campus.charity.common.constant.RoleConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.fabric.ChainClient;
import com.campus.charity.fabric.FabricProperties;
import com.campus.charity.fabric.dto.ChainStatsDTO;
import com.campus.charity.module.admin.entity.Complaint;
import com.campus.charity.module.admin.entity.Publicity;
import com.campus.charity.module.admin.entity.SysBackupRecord;
import com.campus.charity.module.admin.entity.SysOperationLog;
import com.campus.charity.module.admin.mapper.ComplaintMapper;
import com.campus.charity.module.admin.mapper.PublicityMapper;
import com.campus.charity.module.admin.mapper.SysBackupRecordMapper;
import com.campus.charity.module.admin.mapper.SysOperationLogMapper;
import com.campus.charity.module.admin.service.AdminService;
import com.campus.charity.module.audit.mapper.WarningRecordMapper;
import com.campus.charity.module.donation.service.DonationService;
import com.campus.charity.module.evidence.entity.ContractRecord;
import com.campus.charity.module.evidence.mapper.ContractRecordMapper;
import com.campus.charity.module.evidence.service.EvidenceChainService;
import com.campus.charity.module.notice.service.NotificationService;
import com.campus.charity.module.project.entity.Project;
import com.campus.charity.module.project.mapper.ProjectMapper;
import com.campus.charity.module.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台监管服务实现。
 */
@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ProjectMapper projectMapper;
    private final SysOperationLogMapper logMapper;
    private final ComplaintMapper complaintMapper;
    private final PublicityMapper publicityMapper;
    private final SysBackupRecordMapper backupMapper;
    private final ContractRecordMapper contractMapper;
    private final WarningRecordMapper warningMapper;
    private final UserService userService;
    private final DonationService donationService;
    private final NotificationService notificationService;
    private final EvidenceChainService chainService;
    private final ChainClient chainClient;
    private final FabricProperties fabricProperties;
    private final BackupProperties backupProperties;
    private final com.campus.charity.module.admin.service.BackupArchive archives;

    public AdminServiceImpl(ProjectMapper projectMapper,
                            SysOperationLogMapper logMapper,
                            ComplaintMapper complaintMapper,
                            PublicityMapper publicityMapper,
                            SysBackupRecordMapper backupMapper,
                            ContractRecordMapper contractMapper,
                            WarningRecordMapper warningMapper,
                            UserService userService,
                            DonationService donationService,
                            NotificationService notificationService,
                            EvidenceChainService chainService,
                            ChainClient chainClient,
                            FabricProperties fabricProperties,
                            BackupProperties backupProperties, com.campus.charity.module.admin.service.BackupArchive archives) {
        this.projectMapper = projectMapper;
        this.logMapper = logMapper;
        this.complaintMapper = complaintMapper;
        this.publicityMapper = publicityMapper;
        this.backupMapper = backupMapper;
        this.contractMapper = contractMapper;
        this.warningMapper = warningMapper;
        this.userService = userService;
        this.donationService = donationService;
        this.notificationService = notificationService;
        this.chainService = chainService;
        this.chainClient = chainClient;
        this.fabricProperties = fabricProperties;
        this.backupProperties = backupProperties; this.archives=archives;
    }

    @Override
    public Map<String, Object> dashboard() {
        return statistics();
    }

    @Override
    public Map<String, Object> statistics() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userCount", userService.countUsers());
        map.put("userByRole", userService.countByRole());
        map.put("userByMonth", userService.countByMonth());
        map.put("projectCount", projectMapper.selectCount(new LambdaQueryWrapper<>()));
        map.put("projectByStatus", projectMapper.countByStatus());
        map.put("projectByCategory", projectMapper.countByCategory());
        map.put("targetAmount", projectMapper.sumTargetAmount());
        map.put("raisedAmount", projectMapper.sumCurrentAmount());
        map.put("donationTotal", donationService.totalAmount());
        map.put("donationCount", donationService.totalCount());
        map.put("donationTrend", donationService.trend(30));
        map.put("warningUnhandled", warningMapper.countUnhandled());
        map.put("warningByLevel", warningMapper.countByLevel());
        map.put("complaintPending", complaintMapper.selectCount(new LambdaQueryWrapper<Complaint>()
                .in(Complaint::getStatus, Arrays.asList(0, 1))));
        map.put("evidenceCount", chainService.chainStats().getTotalEvidence());
        map.put("chainSource", chainService.chainSource());
        map.put("chaincodeVersion", chainService.chaincodeVersion());
        return map;
    }

    @Override
    public PageResult<SysOperationLog> pageLogs(String module, String keyword, long current, long size) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(module)) {
            wrapper.eq(SysOperationLog::getModule, module);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(SysOperationLog::getOperation, keyword)
                    .or().like(SysOperationLog::getAccount, keyword)
                    .or().like(SysOperationLog::getRealName, keyword));
        }
        wrapper.orderByDesc(SysOperationLog::getLogId);
        IPage<SysOperationLog> page = logMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page);
    }

    @Override
    public List<Map<String, Object>> logStatistics() {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(module("按模块统计", logMapper.countByModule()));
        result.add(module("按日期统计", logMapper.countByDay()));
        return result;
    }

    @Override
    public Long submitComplaint(Complaint complaint) {
        complaint.setComplaintId(null);
        complaint.setComplaintNo("CMP" + System.currentTimeMillis());
        complaint.setUserId(SecurityUtil.getUserId());
        complaint.setStatus(0);
        complaint.setNotifyStatus(0);
        complaint.setCreateTime(LocalDateTime.now());
        complaint.setUpdateTime(LocalDateTime.now());
        complaintMapper.insert(complaint);
        notificationService.sendToRole(RoleConst.ADMIN, "收到新的投诉",
                "用户提交了投诉：「" + complaint.getTitle() + "」，请及时处理。", "COMPLAINT",
                String.valueOf(complaint.getComplaintId()));
        return complaint.getComplaintId();
    }

    @Override
    public PageResult<Complaint> pageComplaints(Integer status, Long projectId, long current, long size) {
        LambdaQueryWrapper<Complaint> wrapper = new LambdaQueryWrapper<>();
        if(!SecurityUtil.isAdmin() && !SecurityUtil.isAuditor()) wrapper.eq(Complaint::getUserId,SecurityUtil.getUserId());
        if (status != null) {
            wrapper.eq(Complaint::getStatus, status);
        }
        if (projectId != null) {
            wrapper.eq(Complaint::getProjectId, projectId);
        }
        wrapper.orderByDesc(Complaint::getComplaintId);
        IPage<Complaint> page = complaintMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page);
    }

    @Override
    public void handleComplaint(Long complaintId, Integer status, String result) {
        Complaint complaint = complaintMapper.selectById(complaintId);
        if (complaint == null) {
            throw new BizException(ResultCode.NOT_FOUND, "投诉记录不存在");
        }
        complaint.setStatus(status);
        complaint.setHandleResult(result);
        complaint.setHandlerId(SecurityUtil.getUserId());
        complaint.setHandleTime(LocalDateTime.now());
        complaint.setUpdateTime(LocalDateTime.now());
        complaintMapper.updateById(complaint);

        // extends 通知相关方：通知投诉人 + 被投诉项目所属社团
        notificationService.send(complaint.getUserId(), "投诉处理结果通知",
                "您的投诉「" + complaint.getTitle() + "」已处理完毕，处理结果：" + result,
                "COMPLAINT", String.valueOf(complaintId));
        if (complaint.getProjectId() != null) {
            Project project = projectMapper.selectById(complaint.getProjectId());
            if (project != null) {
                notificationService.send(project.getClubId(), "投诉处理通知",
                        "您的项目《" + project.getTitle() + "》相关投诉已处理，处理结果：" + result,
                        "COMPLAINT", String.valueOf(complaintId));
            }
        }
        complaint.setNotifyStatus(1);
        complaintMapper.updateById(complaint);
    }

    @Override
    public PageResult<Complaint> pageMyComplaints(long current,long size) {
        return PageResult.of(complaintMapper.selectPage(new Page<>(Math.max(1,current),Math.max(1,Math.min(100,size))),new LambdaQueryWrapper<Complaint>().eq(Complaint::getUserId,SecurityUtil.getUserId()).orderByDesc(Complaint::getComplaintId)));
    }

    @Override
    public PageResult<Publicity> pagePublicity(Integer status, long current, long size) {
        LambdaQueryWrapper<Publicity> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Publicity::getStatus, status);
        }
        wrapper.orderByDesc(Publicity::getPublicityId);
        IPage<Publicity> page = publicityMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page);
    }

    @Override
    public Long publish(Publicity publicity) {
        publicity.setPublicityId(null);
        publicity.setStatus(1);
        publicity.setViewCount(0);
        publicity.setPublishUserId(SecurityUtil.getUserIdOrNull());
        publicity.setPublishUserName(SecurityUtil.getLoginUserOrNull() == null ? "系统"
                : SecurityUtil.getLoginUser().getRealName());
        publicity.setPublishTime(LocalDateTime.now());
        publicity.setUpdateTime(LocalDateTime.now());
        publicityMapper.insert(publicity);
        return publicity.getPublicityId();
    }

    @Override
    public void revokePublicity(Long publicityId) {
        Publicity publicity = publicityMapper.selectById(publicityId);
        if (publicity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "公示记录不存在");
        }
        publicity.setStatus(0);
        publicity.setUpdateTime(LocalDateTime.now());
        publicityMapper.updateById(publicity);
    }

    @Override
    public PageResult<SysBackupRecord> pageBackups(long current, long size) {
        IPage<SysBackupRecord> page = backupMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<SysBackupRecord>().orderByDesc(SysBackupRecord::getBackupId));
        return PageResult.of(page);
    }

    @Override
    public SysBackupRecord runBackup(String backupType) {
        SysBackupRecord record = new SysBackupRecord();
        record.setBackupType(StrUtil.blankToDefault(backupType, "MANUAL"));
        record.setTriggerUserId(SecurityUtil.getUserIdOrNull());
        record.setCreateTime(LocalDateTime.now());
        String stamp = LocalDateTime.now().format(STAMP);
        String fileName = backupProperties.getDatabase() + "_" + stamp + ".sql";
        File dir = new File(backupProperties.getDir());
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("备份目录创建失败：{}", dir.getAbsolutePath());
        }
        File target = new File(dir, fileName);
        record.setBackupName(fileName);
        record.setBackupPath(target.getAbsolutePath());

        long start = System.currentTimeMillis();
        try {
            List<String> command = Arrays.asList(
                    backupProperties.getMysqldumpPath(),
                    "--host=" + backupProperties.getHost(),
                    "--port=" + backupProperties.getPort(),
                    "--user=" + backupProperties.getUsername(),
                    "--single-transaction", "--no-tablespaces", "--routines", "--triggers",
                    "--default-character-set=utf8mb4",
                    backupProperties.getDatabase());
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().put("MYSQL_PWD",backupProperties.getPassword());
            builder.redirectError(new File(dir,fileName+".error.log"));
            builder.redirectOutput(target);
            Process process = builder.start();
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES);
            if(!finished) process.destroyForcibly();
            int exitCode = finished ? process.exitValue() : -1;
            record.setDurationMs(System.currentTimeMillis() - start);
            if (finished && exitCode == 0 && target.exists() && target.length() > 0) {
                record.setStatus("SUCCESS");
                File archive=archives.pack(target);
                record.setBackupName(archive.getName());record.setBackupPath(archive.getAbsolutePath());
                record.setBackupSize(archive.length());
            } else {
                record.setStatus("FAILED");
                record.setErrorMsg("mysqldump 退出码：" + exitCode + "，请确认已安装 MySQL 客户端并配置正确");
            }
        } catch (Exception e) {
            record.setDurationMs(System.currentTimeMillis() - start);
            record.setStatus("FAILED");
            record.setErrorMsg(StrUtil.maxLength(e.getMessage(), 990));
            log.warn("数据库备份失败：{}", e.getMessage());
        }
        backupMapper.insert(record);
        return record;
    }

    @Override
    public List<ContractRecord> listContracts() {
        return contractMapper.selectList(new LambdaQueryWrapper<ContractRecord>()
                .orderByDesc(ContractRecord::getContractId));
    }

    @Override
    public ContractRecord recordContract(ContractRecord record) {
        record.setContractId(null);
        record.setOperatorId(SecurityUtil.getUserIdOrNull());
        record.setOperatorName(SecurityUtil.getLoginUserOrNull() == null ? "系统"
                : SecurityUtil.getLoginUser().getRealName());
        record.setOperationTime(LocalDateTime.now());
        if (StrUtil.isBlank(record.getChaincodeName())) {
            record.setChaincodeName(fabricProperties.getChaincodeName());
        }
        if (StrUtil.isBlank(record.getChannelName())) {
            record.setChannelName(fabricProperties.getChannelName());
        }
        record.setStatus("RECORDED_ONLY");
        // 合约部署 / 升级行为也做链上存证，留存审计证据
        try {
            String dataHash = com.campus.charity.common.util.HashUtil.sha256Hex(
                    "CONTRACT|" + record.getChaincodeName() + "|" + record.getChaincodeVersion()
                            + "|" + record.getOperationType() + "|" + record.getSequenceNo());
            record.setTxId(chainService.submit(com.campus.charity.fabric.dto.EvidencePayload.builder()
                    .bizType(com.campus.charity.common.constant.BizTypeConst.AUDIT_REPORT)
                    .bizId("CONTRACT-" + record.getChaincodeName() + "-" + record.getChaincodeVersion())
                    .projectId("0")
                    .dataHash(dataHash)
                    .status(record.getOperationType())
                    .remark("智能合约部署 / 升级记录存证")
                    .operator(record.getOperatorName())
                    .operatorRole(RoleConst.ADMIN)
                    .functionName("CreateReportEvidence")
                    .build()).getTxId());
        } catch (Exception e) {
            log.warn("合约操作存证失败（不影响记录写入）：{}", e.getMessage());
        }
        contractMapper.insert(record);
        return record;
    }

    @Override
    public Map<String, Object> chainStatus() {
        ChainStatsDTO stats = chainService.chainStats();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", fabricProperties.isEnabled());
        map.put("chainSource", chainService.chainSource());
        map.put("channelName", fabricProperties.getChannelName());
        map.put("chaincodeName", fabricProperties.getChaincodeName());
        map.put("mspId", fabricProperties.getMspId());
        map.put("chaincodeVersion", chainService.chaincodeVersion());
        map.put("totalEvidence", stats.getTotalEvidence());
        map.put("byBizType", stats.getByBizType());
        map.put("latestHash", stats.getLatestHash());
        map.put("chainIntact", chainService.verifyChainIntegrity());
        map.put("nodes", Arrays.asList(
                node("orderer.campus.edu", "OrdererMSP", "排序节点"),
                node("peer0.org1.campus.edu", "Org1MSP", "校方公益管理"),
                node("peer1.org1.campus.edu", "Org1MSP", "校方公益管理"),
                node("peer0.org2.campus.edu", "Org2MSP", "监管审计"),
                node("peer1.org2.campus.edu", "Org2MSP", "监管审计")));
        map.put("endorsementPolicy", "AND('Org1MSP.peer','Org2MSP.peer')");
        map.put("lastContract", listContracts().isEmpty() ? null : listContracts().get(0));
        return map;
    }

    // ---------------------------------------------------------------------------------

    private Map<String, Object> module(String name, List<Map<String, Object>> data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("data", data);
        return map;
    }

    private Map<String, Object> node(String name, String mspId, String role) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("mspId", mspId);
        map.put("role", role);
        map.put("online", null);
        map.put("description", "MOCK".equals(chainService.chainSource())?"模拟拓扑，未连接真实节点":"未执行节点健康探测");
        return map;
    }
}
