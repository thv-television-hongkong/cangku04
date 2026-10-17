package com.campus.charity.module.trace.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.constant.BizTypeConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.fabric.dto.ChainStatsDTO;
import com.campus.charity.fabric.dto.EvidenceDTO;
import com.campus.charity.fabric.dto.ProjectTraceDTO;
import com.campus.charity.fabric.dto.VerifyResultDTO;
import com.campus.charity.module.evidence.entity.EvidenceRecord;
import com.campus.charity.module.evidence.mapper.EvidenceRecordMapper;
import com.campus.charity.module.evidence.service.EvidenceChainService;
import com.campus.charity.module.file.dto.FileDtos;
import com.campus.charity.module.file.entity.FileRecord;
import com.campus.charity.module.file.service.FileService;
import com.campus.charity.module.project.entity.Project;
import com.campus.charity.module.project.mapper.ProjectMapper;
import com.campus.charity.module.trace.dto.TraceDtos;
import com.campus.charity.module.trace.service.TraceService;
import lombok.extern.slf4j.Slf4j;
import com.campus.charity.security.BusinessAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import com.campus.charity.common.util.SecurityUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 溯源查询服务实现。
 *
 * <p>链上为唯一可信源：优先向链码查询；同时提供链下业务时间线用于页面展示，
 * 并且支持“只依赖链上数据”的核验，满足业务库损坏后仍可核验的可靠性需求。</p>
 */
@Slf4j
@Service
public class TraceServiceImpl implements TraceService {

    private final EvidenceChainService chainService;
    private final EvidenceRecordMapper evidenceMapper;
    private final FileService fileService;
    private final ProjectMapper projectMapper;
    private final BusinessAccess access;
    private final JdbcTemplate db;

    public TraceServiceImpl(EvidenceChainService chainService,
                            EvidenceRecordMapper evidenceMapper,
                            FileService fileService,
                            ProjectMapper projectMapper, BusinessAccess access, JdbcTemplate db) {
        this.chainService = chainService;
        this.evidenceMapper = evidenceMapper;
        this.fileService = fileService;
        this.projectMapper = projectMapper; this.access=access; this.db=db;
    }

    @Override
    public TraceDtos.TraceResult query(TraceDtos.TraceQuery query) {
        if(StrUtil.isNotBlank(query.getProjectNo())) { java.util.List<Long> ids=db.queryForList("SELECT project_id FROM project WHERE project_no=? AND deleted=0",Long.class,query.getProjectNo()); if(ids.isEmpty()) throw new BizException(ResultCode.EVIDENCE_NOT_FOUND);query.setProjectId(ids.get(0)); }
        if(StrUtil.isNotBlank(query.getOrderNo())) { java.util.List<Map<String,Object>> orders=db.queryForList("SELECT o.order_id,o.donor_id,p.club_id,d.donation_id FROM donation_order o JOIN project p ON p.project_id=o.project_id LEFT JOIN donation d ON d.order_id=o.order_id WHERE o.order_no=?",query.getOrderNo()); if(orders.isEmpty())throw new BizException(ResultCode.EVIDENCE_NOT_FOUND);Map<String,Object> o=orders.get(0);if(!SecurityUtil.isAdmin()&&!SecurityUtil.isAuditor()&&!SecurityUtil.getUserId().equals(((Number)o.get("donor_id")).longValue())&&!(SecurityUtil.isClub()&&SecurityUtil.getUserId().equals(((Number)o.get("club_id")).longValue())))throw new BizException(ResultCode.FORBIDDEN);if(o.get("donation_id")==null)throw new BizException(ResultCode.EVIDENCE_NOT_FOUND,"订单尚未形成有效捐赠");query.setBizType("DONATION");query.setBizId(String.valueOf(o.get("donation_id"))); }
        if(StrUtil.isNotBlank(query.getEvidenceNo())) { java.util.List<String> txs=db.queryForList("SELECT tx_id FROM evidence_record WHERE evidence_no=? AND status='SUCCESS'",String.class,query.getEvidenceNo());if(txs.isEmpty())throw new BizException(ResultCode.EVIDENCE_NOT_FOUND);query.setTxId(txs.get(0)); }
        TraceDtos.TraceResult result = new TraceDtos.TraceResult();
        result.setChainIntact(chainService.verifyChainIntegrity());
        result.setChainInfo(chainSummary());

        if (StrUtil.isNotBlank(query.getTxId())) {
            // 维度一：交易ID
            result.setDimension("TX");
            EvidenceDTO evidence = chainService.queryByTxId(query.getTxId().trim());
            if (evidence == null) {
                // 回退到链下凭证（链上不可达时仍可展示）
                EvidenceRecord record = chainService.findByTxId(query.getTxId().trim());
                if (record == null) {
                    throw new BizException(ResultCode.EVIDENCE_NOT_FOUND);
                }
                if(!access.evidence(record.getBizType(),record.getBizId(),record.getProjectId()))throw new BizException(ResultCode.FORBIDDEN);
                result.getRecords().add(toRecordMap(record));
                result.setMessage("适配器未返回该事件，当前仅展示本地凭证副本；尚未完成核验");
                return result;
            }
            requireEvidence(evidence);
            result.getRecords().add(toEvidenceMap(evidence));
            result.setMessage("已按交易ID查询到链上存证");
            return result;
        }

        if (query.getBlockHeight() != null) {
            // 维度二：区块高度
            result.setDimension("BLOCK");
            List<EvidenceDTO> list = chainService.queryByBlockHeight(query.getBlockHeight());
            if (list == null || list.isEmpty()) {
                List<Map<String, Object>> local = evidenceMapper.selectDetailByBlockHeight(query.getBlockHeight());
                if (local == null || local.isEmpty()) {
                    throw new BizException(ResultCode.EVIDENCE_NOT_FOUND, "该区块高度未找到存证记录");
                }
                if(!SecurityUtil.isAdmin()&&!SecurityUtil.isAuditor())throw new BizException(ResultCode.FORBIDDEN);
                result.setRecords(local);
                result.setMessage("适配器未返回该区块事件，当前仅展示本地副本；尚未完成核验");
                return result;
            }
            result.setRecords(list.stream().filter(e->access.evidence(e.getBizType(),e.getBizId(),projectId(e))).map(this::toEvidenceMap).collect(Collectors.toList()));
            result.setMessage("已按区块高度查询到链上存证");
            return result;
        }

        if (query.getProjectId() != null) {
            // 维度三：项目ID（全流程）
            return projectTrace(query.getProjectId());
        }

        if (StrUtil.isNotBlank(query.getBizType()) && StrUtil.isNotBlank(query.getBizId())) {
            // 维度四：业务类型 + 业务ID
            result.setDimension("BIZ");
            EvidenceDTO evidence = chainService.queryEvidence(query.getBizType().toUpperCase(), query.getBizId().trim());
            if (evidence == null) {
                Map<String, Object> local = evidenceMapper.selectDetailByBiz(query.getBizType().toUpperCase(), query.getBizId().trim());
                if (local == null) {
                    throw new BizException(ResultCode.EVIDENCE_NOT_FOUND);
                }
                if(!SecurityUtil.isAdmin()&&!SecurityUtil.isAuditor())throw new BizException(ResultCode.FORBIDDEN);
                result.setRecords(new ArrayList<>(java.util.Collections.singletonList(local)));
                result.setMessage("适配器未返回该事件，当前仅展示本地副本；尚未完成核验");
                return result;
            }
            requireEvidence(evidence);
            result.getRecords().add(toEvidenceMap(evidence));
            result.setMessage("已按业务ID查询到链上存证");
            return result;
        }

        throw new BizException(ResultCode.BAD_REQUEST, "请至少提供项目ID、交易ID、区块高度或业务ID中的一项");
    }

    @Override
    public TraceDtos.VerifyResult verify(TraceDtos.VerifyForm form) {
        if (form.getFileId() != null) {
            return verifyFile(form.getFileId());
        }
        if (StrUtil.isBlank(form.getBizType()) || StrUtil.isBlank(form.getBizId()) || StrUtil.isBlank(form.getHash())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请提供业务类型、业务ID与待校验哈希");
        }
        assertVerificationAccess(form);
        VerifyResultDTO dto = chainService.verify(form.getBizType().toUpperCase(), form.getBizId().trim(), form.getHash().trim());
        TraceDtos.VerifyResult result = new TraceDtos.VerifyResult();
        result.setMatched(dto.isMatched());
        result.setOutcome(dto.getOutcome());
        result.setMessage(dto.getMessage());
        result.setBizType(dto.getBizType());
        result.setBizId(dto.getBizId());
        result.setProvidedHash(dto.getProvidedHash());
        result.setStoredHash(dto.getStoredHash());
        result.setTxId(dto.getTxId());
        result.setBlockHeight(dto.getBlockHeight());
        result.setChainSeq(dto.getChainSeq());
        result.setChainTime(dto.getChainTime());
        result.setChainSource(chainService.chainSource());
        fillFileInfo(result, form.getBizType().toUpperCase(), form.getBizId().trim());
        return result;
    }

    @Override
    public TraceDtos.VerifyResult verifyByChainOnly(TraceDtos.VerifyForm form) {
        if (StrUtil.isBlank(form.getBizType()) || StrUtil.isBlank(form.getBizId()) || StrUtil.isBlank(form.getHash())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请提供业务类型、业务ID与待校验哈希");
        }
        assertVerificationAccess(form);
        VerifyResultDTO dto = chainService.verifyByChainOnly(form.getBizType().toUpperCase(), form.getBizId().trim(), form.getHash().trim());
        TraceDtos.VerifyResult result = new TraceDtos.VerifyResult();
        result.setMatched(dto.isMatched());
        result.setOutcome(dto.getOutcome());
        result.setMessage(dto.getMessage());
        result.setBizType(dto.getBizType());
        result.setBizId(dto.getBizId());
        result.setProvidedHash(dto.getProvidedHash());
        result.setStoredHash(dto.getStoredHash());
        result.setTxId(dto.getTxId());
        result.setBlockHeight(dto.getBlockHeight());
        result.setChainSeq(dto.getChainSeq());
        result.setChainTime(dto.getChainTime());
        result.setChainSource(chainService.chainSource());
        return result;
    }

    @Override
    public TraceDtos.TraceResult projectTrace(Long projectId) {
        access.requireProject(projectId);
        TraceDtos.TraceResult result = new TraceDtos.TraceResult();
        result.setDimension("PROJECT");
        ProjectTraceDTO trace = chainService.projectTrace(projectId);
        result.setChainIntact(trace.getChainIntact());
        result.setChainInfo(chainSummary());
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (EvidenceDTO evidence : trace.getTimeline() == null ? new ArrayList<EvidenceDTO>() : trace.getTimeline()) {
            if(!access.evidence(evidence.getBizType(),evidence.getBizId(),projectId)) continue;
            Map<String, Object> node = toEvidenceMap(evidence);
            node.put("nodeType", "CHAIN");
            timeline.add(node);
        }
        // 补充链下事件节点（项目审核、投诉处置等）
        for (Map<String, Object> audit : evidenceMapper.selectDetailByProject(projectId)) {
            if(!SecurityUtil.isAdmin()&&!SecurityUtil.isAuditor()) continue;
            Map<String, Object> node = new LinkedHashMap<>(audit);
            node.put("nodeType", "OFF_CHAIN");
            timeline.add(node);
        }
        result.setTimeline(timeline);
        result.setRecords(timeline);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("evidenceCount", trace.getEvidenceCount());
        summary.put("donationCount", trace.getDonationCount());
        summary.put("donationTotal", db.queryForObject("SELECT current_amount FROM project WHERE project_id=?",java.math.BigDecimal.class,projectId));
        summary.put("refundTotal", db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM refund_application WHERE project_id=? AND status='REFUNDED'",java.math.BigDecimal.class,projectId));
        summary.put("fundUsageCount", trace.getFundUsageCount());
        summary.put("fundUsageTotal", trace.getFundUsageTotal());
        summary.put("withdrawalCount", trace.getWithdrawalCount());
        summary.put("withdrawalTotal", trace.getWithdrawalTotal());
        summary.put("materialCount", trace.getMaterialCount());
        summary.put("latestBlockHeight", trace.getLatestBlockHeight());
        summary.put("latestChainHash", trace.getLatestChainHash());
        result.setChainInfo(summary);
        result.setMessage("项目全流程溯源完成，共 " + timeline.size() + " 个溯源节点");
        return result;
    }

    @Override
    public TraceDtos.VerifyResult verifyFile(Long fileId) {
        TraceDtos.VerifyResult r=new TraceDtos.VerifyResult();r.setFileId(fileId);r.setChainSource(chainService.chainSource());
        try {
            FileDtos.FileVerifyVO local=fileService.verifyByStoredFile(fileId);
            FileRecord f=fileService.getById(fileId);r.setProvidedHash(local.getUploadedHash());r.setFileName(f.getOriginalName());
            java.util.List<EvidenceRecord> records=evidenceMapper.selectList(new LambdaQueryWrapper<EvidenceRecord>().eq(EvidenceRecord::getFileId,fileId).eq(EvidenceRecord::getStatus,"SUCCESS").orderByDesc(EvidenceRecord::getEvidenceId).last("LIMIT 1"));
            if(records.isEmpty()){r.setOutcome("NOT_FOUND");r.setMessage("尚无该文件的已提交存证；本地比对不等于存证核验");return r;}
            EvidenceDTO stored=chainService.queryByTxId(records.get(0).getTxId());
            if(stored==null || StrUtil.isBlank(stored.getFileHash())){r.setOutcome("NOT_FOUND");r.setMessage("未找到文件摘要存证；业务摘要不能当作文件摘要");return r;}
            r.setStoredHash(stored.getFileHash());r.setTxId(stored.getTxId());r.setBlockHeight(stored.getBlockHeight());
            r.setMatched(local.isMatched()&&local.getUploadedHash().equalsIgnoreCase(stored.getFileHash()));r.setOutcome(r.isMatched()?"MATCH":"MISMATCH");r.setMessage(r.isMatched()?"原件字节摘要与登记文件摘要一致，不证明材料内容真实":"原件摘要与登记摘要不一致");
        } catch(BizException e){if(e.getCode()==403)throw e;r.setOutcome("UNAVAILABLE");r.setMessage("原件或存证适配器不可访问，暂时无法核验");}
        catch(Exception e){r.setOutcome("UNAVAILABLE");r.setMessage("核验服务暂不可用");}
        return r;
    }

    @Override
    public List<Map<String, Object>> exportEvidence(TraceDtos.TraceQuery query) {
        LambdaQueryWrapper<EvidenceRecord> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StrUtil.isNotBlank(query.getBizType())) {
                wrapper.eq(EvidenceRecord::getBizType, query.getBizType().toUpperCase());
            }
            if (query.getProjectId() != null) {
                wrapper.eq(EvidenceRecord::getProjectId, query.getProjectId());
            }
            if (query.getBlockHeight() != null) {
                wrapper.eq(EvidenceRecord::getBlockHeight, query.getBlockHeight());
            }
        }
        wrapper.orderByAsc(EvidenceRecord::getEvidenceId);
        return evidenceMapper.selectList(wrapper).stream().filter(record -> access.evidence(record.getBizType(),record.getBizId(),record.getProjectId())).map(record -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("存证编号", record.getEvidenceNo());
            map.put("业务类型", BizTypeConst.label(record.getBizType()));
            map.put("关联业务ID", record.getBizId());
            map.put("项目ID", record.getProjectId());
            map.put("存证数据哈希", record.getDataHash());
            map.put("交易ID", record.getTxId());
            map.put("区块高度", record.getBlockHeight());
            map.put("上链时间", record.getChainTime());
            map.put("通道", record.getChannelName());
            map.put("链码", record.getChaincodeName());
            map.put("链码方法", record.getFunctionName());

            map.put("存证状态", record.getStatus());
            map.put("存证来源", record.getChainSource());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> chainOverview() {
        Map<String, Object> map = chainSummary();
        map.put("byBizTypeLabel", ((ChainStatsDTO) map.get("stats")).getByBizType().entrySet().stream()
                .collect(Collectors.toMap(e -> BizTypeConst.label(e.getKey()), Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new)));
        map.put("chainIntact", chainService.verifyChainIntegrity());
        map.put("maxBlockHeight", evidenceMapper.selectMaxBlockHeight());
        map.put("evidenceTotal", evidenceMapper.countSuccess());
        map.put("channelName", "charitychannel");
        map.put("chaincodeName", "charity-cc");
        map.put("endorsementPolicy", "AND('Org1MSP.peer','Org2MSP.peer')");
        return map;
    }

    // ---------------------------------------------------------------------------------

    private Long projectId(EvidenceDTO e){try{return Long.valueOf(e.getProjectId());}catch(Exception ex){return null;}}
    private void assertVerificationAccess(TraceDtos.VerifyForm f){
        if(SecurityUtil.isAdmin()||SecurityUtil.isAuditor())return;
        EvidenceRecord record=chainService.findByBiz(f.getBizType().toUpperCase(),f.getBizId().trim());
        if(record!=null&&!access.evidence(record.getBizType(),record.getBizId(),record.getProjectId()))throw new BizException(ResultCode.FORBIDDEN);
    }
    private void requireEvidence(EvidenceDTO e){if(!access.evidence(e.getBizType(),e.getBizId(),projectId(e)))throw new BizException(ResultCode.FORBIDDEN);}

    private Map<String, Object> chainSummary() {
        Map<String, Object> map = new LinkedHashMap<>();
        ChainStatsDTO stats = chainService.chainStats();
        map.put("stats", stats);
        map.put("totalEvidence", stats.getTotalEvidence());
        map.put("projectCount", stats.getProjectCount());
        map.put("latestHash", stats.getLatestHash());
        map.put("chaincodeVersion", stats.getChaincodeVersion());
        map.put("chainSource", chainService.chainSource());
        return map;
    }

    private Map<String, Object> toEvidenceMap(EvidenceDTO evidence) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bizType", evidence.getBizType());
        map.put("bizTypeLabel", BizTypeConst.label(evidence.getBizType()));
        map.put("bizId", evidence.getBizId());
        map.put("projectId", evidence.getProjectId());
        map.put("dataHash", evidence.getDataHash());
        map.put("fileHash", evidence.getFileHash());
        map.put("amount", evidence.getAmount());
        map.put("status", evidence.getStatus());
        map.put("txId", evidence.getTxId());
        map.put("chainSeq", evidence.getChainSeq());
        map.put("blockHeight", evidence.getBlockHeight());
        map.put("chainTime", evidence.getTimestamp());
        map.put("prevChainHash", evidence.getPrevChainHash());
        map.put("chainHash", evidence.getChainHash());
        map.put("operator", evidence.getOperator());
        map.put("chainSource", chainService.chainSource());
        return map;
    }

    private Map<String, Object> toRecordMap(EvidenceRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("evidenceNo", record.getEvidenceNo());
        map.put("bizType", record.getBizType());
        map.put("bizTypeLabel", BizTypeConst.label(record.getBizType()));
        map.put("bizId", record.getBizId());
        map.put("projectId", record.getProjectId());
        map.put("dataHash", record.getDataHash());
        map.put("txId", record.getTxId());
        map.put("blockHeight", record.getBlockHeight());
        map.put("chainTime", record.getChainTime());
        map.put("chainSource", record.getChainSource());
        return map;
    }

    private void fillFileInfo(TraceDtos.VerifyResult result, String bizType, String bizId) {
        try {
            Map<String, Object> detail = evidenceMapper.selectDetailByBiz(bizType, bizId);
            if (detail != null) {
                Object fileId = detail.get("fileId");
                if (fileId != null) {
                    FileRecord file = fileService.getById(Long.parseLong(String.valueOf(fileId)));
                    result.setFileId(file.getFileId());
                    result.setFileName(file.getOriginalName());
                    result.setFileUrl(file.getFileUrl());
                }
            }
        } catch (Exception ignored) {
            // 文件信息缺失不影响校验结论
        }
    }

    private String mapFileBizType(String fileBizType) {
        if (fileBizType == null) {
            return null;
        }
        switch (fileBizType) {
            case "PROJECT_MATERIAL":
                return BizTypeConst.MATERIAL;
            case "FUND_USAGE":
                return BizTypeConst.FUND_USAGE;
            case "AUDIT_REPORT":
                return BizTypeConst.AUDIT_REPORT;
            default:
                return null;
        }
    }
}
