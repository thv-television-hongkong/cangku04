package com.campus.charity.module.audit.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.config.FileProperties;
import com.campus.charity.common.constant.BizTypeConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.HashUtil;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.fabric.dto.ChainResultDTO;
import com.campus.charity.fabric.dto.EvidencePayload;
import com.campus.charity.module.audit.entity.AuditReport;
import com.campus.charity.module.audit.mapper.AuditReportMapper;
import com.campus.charity.module.audit.mapper.WarningRecordMapper;
import com.campus.charity.module.audit.service.AuditReportService;
import com.campus.charity.module.donation.mapper.DonationMapper;
import com.campus.charity.module.evidence.entity.EvidenceRecord;
import com.campus.charity.module.evidence.mapper.EvidenceRecordMapper;
import com.campus.charity.module.evidence.service.EvidenceChainService;
import com.campus.charity.module.file.entity.FileRecord;
import com.campus.charity.module.file.mapper.FileRecordMapper;
import com.campus.charity.module.project.entity.Project;
import com.campus.charity.module.project.mapper.ProjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计报告服务实现。
 */
@Slf4j
@Service
public class AuditReportServiceImpl implements AuditReportService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AuditReportMapper reportMapper;
    private final EvidenceRecordMapper evidenceMapper;
    private final DonationMapper donationMapper;
    private final WarningRecordMapper warningMapper;
    private final ProjectMapper projectMapper;
    private final FileRecordMapper fileRecordMapper;
    private final EvidenceChainService chainService;
    private final FileProperties fileProperties;
    private final org.springframework.jdbc.core.JdbcTemplate db;

    public AuditReportServiceImpl(AuditReportMapper reportMapper,
                                  EvidenceRecordMapper evidenceMapper,
                                  DonationMapper donationMapper,
                                  WarningRecordMapper warningMapper,
                                  ProjectMapper projectMapper,
                                  FileRecordMapper fileRecordMapper,
                                  EvidenceChainService chainService,
                                  FileProperties fileProperties, org.springframework.jdbc.core.JdbcTemplate db) {
        this.reportMapper = reportMapper;
        this.evidenceMapper = evidenceMapper;
        this.donationMapper = donationMapper;
        this.warningMapper = warningMapper;
        this.projectMapper = projectMapper;
        this.fileRecordMapper = fileRecordMapper;
        this.chainService = chainService;
        this.fileProperties = fileProperties;
        this.db = db;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuditReport generate(String reportType, Long projectId, LocalDateTime startTime, LocalDateTime endTime) {
        String type = StrUtil.blankToDefault(reportType, "COMPREHENSIVE");
        Project project = projectId == null ? null : projectMapper.selectById(projectId);
        if(projectId!=null && project==null) throw new BizException(ResultCode.BAD_REQUEST,"项目不存在");
        if(startTime!=null && endTime!=null && startTime.isAfter(endTime)) throw new BizException(ResultCode.BAD_REQUEST,"开始时间不能晚于结束时间");

        // 1) 汇总统计
        List<EvidenceRecord> evidenceList = buildEvidenceList(projectId, startTime, endTime);
        List<Object> args=new ArrayList<>();
        String filter=" WHERE deleted=0 AND status='CONFIRMED' AND confirmation_id IS NOT NULL AND refund_status<>'REFUNDED'";
        if(projectId!=null){filter+=" AND project_id=?";args.add(projectId);}
        if(startTime!=null){filter+=" AND confirm_time>=?";args.add(startTime);}
        if(endTime!=null){filter+=" AND confirm_time<=?";args.add(endTime);}
        BigDecimal donationTotal=db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM donation"+filter,BigDecimal.class,args.toArray());
        long donationCount=db.queryForObject("SELECT COUNT(*) FROM donation"+filter,Long.class,args.toArray());
        LambdaQueryWrapper<com.campus.charity.module.audit.entity.WarningRecord> warningFilter=new LambdaQueryWrapper<>();
        if(projectId!=null) warningFilter.eq(com.campus.charity.module.audit.entity.WarningRecord::getProjectId,projectId);
        if(startTime!=null) warningFilter.ge(com.campus.charity.module.audit.entity.WarningRecord::getCreateTime,startTime);
        if(endTime!=null) warningFilter.le(com.campus.charity.module.audit.entity.WarningRecord::getCreateTime,endTime);
        long warningCount=warningMapper.selectCount(warningFilter);

        // 2) 哈希核验：比对链上存证哈希与业务库副本
        Map<String,Integer> outcomes=new LinkedHashMap<>();
        for(String outcome:new String[]{"MATCH","MISMATCH","NOT_FOUND","UNAVAILABLE"})outcomes.put(outcome,0);
        Map<Long,String> recordOutcomes=new LinkedHashMap<>();
        for (EvidenceRecord record : evidenceList) {
            String outcome;
            try {
                // 按历史交易核验原事件，不能把历史摘要与该业务的最新版本比较。
                com.campus.charity.fabric.dto.EvidenceDTO event=chainService.queryByTxId(record.getTxId());
                outcome=event==null?"NOT_FOUND":record.getDataHash()!=null && record.getDataHash().equalsIgnoreCase(event.getDataHash())?"MATCH":"MISMATCH";
            } catch(Exception e) { outcome="UNAVAILABLE"; }
            outcomes.put(outcome,outcomes.get(outcome)+1);
            recordOutcomes.put(record.getEvidenceId(),outcome);
        }
        String conclusion=conclusion(evidenceList.size(),outcomes);

        // 3) 生成报告文件（链下存储，报告文件哈希上链存证）
        String content = buildReportContent(type, project, startTime, endTime, evidenceList,
                donationTotal, donationCount, warningCount, conclusion, recordOutcomes);
        FileRecord fileRecord = writeReportFile(content, type);

        AuditReport report = new AuditReport();
        report.setReportNo("RPT" + System.currentTimeMillis());
        report.setTitle((project == null ? "校园公益众筹平台" : project.getTitle()) + auditTypeLabel(type) + "报告");
        report.setReportType(type);
        report.setProjectId(projectId);
        report.setStartTime(startTime);
        report.setEndTime(endTime);
        report.setSummary(buildSummary(project, evidenceList.size(), donationTotal, donationCount, warningCount)+conclusion);
        report.setTotalAmount(donationTotal == null ? BigDecimal.ZERO : donationTotal);
        report.setDonationCount((int) donationCount);
        report.setEvidenceCount(evidenceList.size());
        report.setWarningCount((int) warningCount);
        report.setVerifyResult(evidenceList.isEmpty()?"NO_EVIDENCE":outcomes.get("MISMATCH")>0?"MISMATCH":outcomes.get("MATCH")==evidenceList.size()?"ALL_MATCH":"UNVERIFIED");
        report.setFileId(fileRecord == null ? null : fileRecord.getFileId());
        report.setFileUrl(fileRecord == null ? null : fileRecord.getFileUrl());
        report.setFileHash(fileRecord == null ? null : fileRecord.getFileHash());
        report.setGenerateUserId(SecurityUtil.getUserId());
        report.setGenerateUserName(SecurityUtil.getLoginUser().getRealName());
        report.setGenerateTime(LocalDateTime.now());
        reportMapper.insert(report);

        // 4) 报告哈希上链存证（监管审计留痕）
        {
            String dataHash = HashUtil.reportHash(report.getReportId(), type, report.getFileHash());
            ChainResultDTO chainResult = chainService.submit(EvidencePayload.builder()
                    .bizType(BizTypeConst.AUDIT_REPORT)
                    .bizId(String.valueOf(report.getReportId()))
                    .projectId(projectId == null ? "0" : String.valueOf(projectId))
                    .dataHash(dataHash)
                    .fileHash(report.getFileHash())
                    .status(type)
                    .operator(SecurityUtil.getLoginUser().getAccount())
                    .operatorRole(SecurityUtil.getLoginUser().getRole())
                    .operatorId(SecurityUtil.getUserId())
                    .operatorName(SecurityUtil.getLoginUser().getRealName())
                    .fileId(report.getFileId())
                    .functionName("CreateReportEvidence")
                    .build());
            report.setTxId(chainResult.getTxId());
            report.setBlockHeight(chainResult.getBlockHeight());
            reportMapper.updateById(report);
        }
        log.info("审计报告生成完成 reportNo={} evidenceCount={} verifyResult={}",
                report.getReportNo(), report.getEvidenceCount(), report.getVerifyResult());
        return report;
    }

    @Override
    public PageResult<AuditReport> page(Long projectId, String reportType, long current, long size) {
        LambdaQueryWrapper<AuditReport> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(AuditReport::getProjectId, projectId);
        }
        if (StrUtil.isNotBlank(reportType)) {
            wrapper.eq(AuditReport::getReportType, reportType);
        }
        wrapper.orderByDesc(AuditReport::getReportId);
        IPage<AuditReport> page = reportMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page);
    }

    @Override
    public AuditReport getById(Long reportId) {
        AuditReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ResultCode.REPORT_ERROR, "审计报告不存在");
        }
        return report;
    }

    @Override
    public List<Map<String, Object>> exportRows(Long reportId) {
        AuditReport report = getById(reportId);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<EvidenceRecord> list = buildEvidenceList(report.getProjectId(), report.getStartTime(), report.getEndTime());
        for (EvidenceRecord record : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("存证编号", record.getEvidenceNo());
            row.put("业务类型", BizTypeConst.label(record.getBizType()));
            row.put("关联业务ID", record.getBizId());
            row.put("项目ID", record.getProjectId());
            row.put("存证哈希", record.getDataHash());
            row.put("交易ID", record.getTxId());
            row.put("区块高度", record.getBlockHeight());
            row.put("上链时间", record.getChainTime());
            row.put("操作人", record.getOperatorName());
            row.put("存证来源", record.getChainSource());
            rows.add(row);
        }
        return rows;
    }

    @Override
    public void delete(Long reportId) {
        reportMapper.deleteById(reportId);
    }

    // ---------------------------------------------------------------------------------

    private List<EvidenceRecord> buildEvidenceList(Long projectId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<EvidenceRecord> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(EvidenceRecord::getProjectId, projectId);
        }
        if (startTime != null) {
            wrapper.ge(EvidenceRecord::getChainTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(EvidenceRecord::getChainTime, endTime);
        }
        wrapper.orderByAsc(EvidenceRecord::getEvidenceId);
        return evidenceMapper.selectList(wrapper);
    }

    private String buildSummary(Project project, int evidenceCount, BigDecimal donationTotal,
                                long donationCount, long warningCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("本次审计共核查链上存证 ").append(evidenceCount).append(" 条；");
        sb.append("捐赠记录 ").append(donationCount).append(" 笔，金额合计 ")
                .append(donationTotal == null ? "0.00" : donationTotal.toPlainString()).append(" 元；");
        sb.append("异常交易预警 ").append(warningCount).append(" 条。");
        if (project != null) {
            sb.append("项目《").append(project.getTitle()).append("》当前筹款 ")
                    .append(project.getCurrentAmount().toPlainString()).append(" 元 / 目标 ")
                    .append(project.getTargetAmount().toPlainString()).append(" 元；");
        }
        return sb.toString();
    }

    private String conclusion(int total,Map<String,Integer> outcomes) {
        String source="MOCK".equals(chainService.chainSource())?"MOCK 本地模拟记录（不代表真实联盟链）":"Fabric 账本";
        if(total==0)return "本次范围内无已提交存证，尚无核验结论。来源："+source+"。";
        return "一致 "+outcomes.get("MATCH")+" 条，不一致 "+outcomes.get("MISMATCH")+" 条，未找到 "+outcomes.get("NOT_FOUND")+" 条，暂不可用 "+outcomes.get("UNAVAILABLE")+" 条。来源："+source+"。摘要一致只说明记录一致性，不证明材料内容真实。";
    }

    private String buildReportContent(String type, Project project, LocalDateTime startTime, LocalDateTime endTime,
                                      List<EvidenceRecord> evidenceList, BigDecimal donationTotal,
                                      long donationCount, long warningCount, String conclusion,Map<Long,String> recordOutcomes) {
        StringBuilder sb = new StringBuilder();
        sb.append("校园公益众筹存证溯源系统\n");
        sb.append(auditTypeLabel(type)).append("报告\n");
        sb.append("===========================================\n");
        sb.append("生成时间：").append(LocalDateTime.now().format(TIME)).append("\n");
        sb.append("统计区间：").append(startTime == null ? "不限" : startTime.format(TIME))
                .append(" ~ ").append(endTime == null ? "不限" : endTime.format(TIME)).append("\n");
        if (project != null) {
            sb.append("项目名称：").append(project.getTitle()).append("\n");
            sb.append("项目编号：").append(project.getProjectNo()).append("\n");
            sb.append("帮扶对象（脱敏）：").append(project.getBeneficiaryMasked()).append("\n");
        }
        sb.append("链上存证条数：").append(evidenceList.size()).append("\n");
        sb.append("捐赠笔数：").append(donationCount).append("\n");
        sb.append("捐赠金额：").append(donationTotal == null ? "0.00" : donationTotal.toPlainString()).append(" 元\n");
        sb.append("异常预警条数：").append(warningCount).append("\n");
        sb.append("哈希核验结论：").append(conclusion).append("\n");
        sb.append("金额口径：按确认时间筛选，统计截至报告生成时仍有效的已确认捐赠净额，已完成全额退款不计入。\n");
        sb.append("存证来源：").append(chainService.chainSource()).append("\n");
        sb.append("链码版本：").append(chainService.chaincodeVersion()).append("\n");
        sb.append("===========================================\n");
        sb.append("存证明细（链上凭证）：\n");
        for (EvidenceRecord record : evidenceList) {
            sb.append("-").append(record.getEvidenceNo())
                    .append(" | ").append(BizTypeConst.label(record.getBizType()))
                    .append(" | 业务ID:").append(record.getBizId())
                    .append(" | 哈希:").append(record.getDataHash())
                    .append(" | 交易ID:").append(record.getTxId())
                    .append(" | 区块高度:").append(record.getBlockHeight())
                    .append(" | 核验:").append(recordOutcomes.get(record.getEvidenceId()))
                    .append(" | 来源:").append(record.getChainSource())
                    .append(" | 上链时间:").append(record.getChainTime() == null ? "" : record.getChainTime().format(TIME))
                    .append("\n");
        }
        sb.append("===========================================\n");
        sb.append("声明：本报告仅针对校园内部公益众筹业务，链上仅存文件哈希摘要，\n");
        sb.append("      原始文件与用户敏感信息均保存在学校业务服务器，未写入区块链。\n");
        return sb.toString();
    }

    private FileRecord writeReportFile(String content, String type) {
        try {
            String day = LocalDate.now().format(DAY);
            File dir = new File(fileProperties.getUploadPath(), "AUDIT_REPORT" + File.separator + day);
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("审计报告目录创建失败：{}", dir.getAbsolutePath());
            }
            String fileName = "audit_report_" + IdUtil.fastSimpleUUID() + ".txt";
            File target = new File(dir, fileName);
            Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8));
            String hash = HashUtil.sha256File(target);

            FileRecord record = new FileRecord();
            record.setFileNo("FILE" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase());
            record.setOriginalName("审计报告_" + type + "_" + day + ".txt");
            record.setStoredName(fileName);
            record.setFilePath(target.getAbsolutePath());
            record.setFileHash(hash);
            record.setFileSize(target.length());
            record.setContentType("text/plain");
            record.setBizType("AUDIT_REPORT");
            record.setUploaderId(SecurityUtil.getUserIdOrNull());
            record.setOnChain(0);
            record.setHashVerified(1);
            record.setDeleted(0);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            fileRecordMapper.insert(record);
            record.setFileUrl(fileProperties.getUrlPrefix() + record.getFileId());
            fileRecordMapper.updateById(record);
            return record;
        } catch (Exception e) {
            log.warn("审计报告文件生成失败 type={}", e.getClass().getSimpleName());
            throw new BizException(ResultCode.REPORT_ERROR,"报告原件生成失败，请稍后重试");
        }
    }

    private String auditTypeLabel(String type) {
        if (type == null) {
            return "综合审计";
        }
        switch (type) {
            case "PROJECT":
                return "项目审计";
            case "FUND":
                return "资金审计";
            case "EVIDENCE":
                return "存证审计";
            default:
                return "综合审计";
        }
    }
}
