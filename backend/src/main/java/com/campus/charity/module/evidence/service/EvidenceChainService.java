package com.campus.charity.module.evidence.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.constant.BizTypeConst;
import com.campus.charity.common.constant.CommonConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.HashUtil;
import com.campus.charity.common.util.PrivacyUtil;
import com.campus.charity.fabric.ChainClient;
import com.campus.charity.fabric.FabricProperties;
import com.campus.charity.fabric.dto.ChainResultDTO;
import com.campus.charity.fabric.dto.ChainStatsDTO;
import com.campus.charity.fabric.dto.EvidenceDTO;
import com.campus.charity.fabric.dto.EvidencePayload;
import com.campus.charity.fabric.dto.ProjectTraceDTO;
import com.campus.charity.fabric.dto.VerifyResultDTO;
import com.campus.charity.module.evidence.entity.ChainEvidenceSnapshot;
import com.campus.charity.module.evidence.entity.EvidenceRecord;
import com.campus.charity.module.evidence.mapper.ChainEvidenceSnapshotMapper;
import com.campus.charity.module.evidence.mapper.EvidenceRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.Locale;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 区块链存证服务（业务层与联盟链的唯一边界）。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>上链前执行隐私合规校验（链下预检）；</li>
 *   <li>调用链码写入哈希摘要，取得交易ID + 区块高度 + 链式哈希；</li>
 *   <li>在业务库保存存证凭证（ER 实体 4）与链上快照，便于业务查询与业务库损坏时核验；</li>
 *   <li>失败时记录失败凭证，便于后台审计追踪。</li>
 * </ol>
 */
@Slf4j
@Service
public class EvidenceChainService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChainClient chainClient;
    private final FabricProperties fabricProperties;
    private final EvidenceRecordMapper evidenceRecordMapper;
    private final ChainEvidenceSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate db;

    public EvidenceChainService(ChainClient chainClient,
                                FabricProperties fabricProperties,
                                EvidenceRecordMapper evidenceRecordMapper,
                                ChainEvidenceSnapshotMapper snapshotMapper,
                                ObjectMapper objectMapper, JdbcTemplate db) {
        this.chainClient = chainClient;
        this.fabricProperties = fabricProperties;
        this.evidenceRecordMapper = evidenceRecordMapper;
        this.snapshotMapper = snapshotMapper;
        this.objectMapper = objectMapper;
        this.db = db;
    }

    /** 当前存证来源：FABRIC 真实链 / MOCK 模拟链 */
    public String chainSource() {
        return chainClient.source();
    }

    public boolean fabricEnabled() {
        return fabricProperties.isEnabled();
    }

    /** 按业务类型和业务 ID 查询当前链上存证；由溯源模块统一通过本服务访问链客户端。 */
    public EvidenceDTO queryEvidence(String bizType, String bizId) {
        return chainClient.queryEvidence(bizType, bizId);
    }

    /**
     * 写入存证（项目摘要 / 捐赠记录 / 资金凭证 / 帮扶材料 / 资金提取 / 项目进度 / 审计报告）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChainResultDTO submit(EvidencePayload payload) {
        PrivacyUtil.validate(payload);
        payload.setDataHash(payload.getDataHash().toLowerCase(Locale.ROOT));
        if (StrUtil.isBlank(payload.getEventType())) payload.setEventType(StrUtil.blankToDefault(payload.getStatus(),"RECORDED"));
        if (StrUtil.isBlank(payload.getEventKey())) payload.setEventKey(payload.getBizType()+":"+payload.getBizId()+":"+payload.getDataHash());
        // 链上不传用户账号、姓名、自由文本或客户端扩展信息。
        payload.setOperator("SCHOOL_SYSTEM"); payload.setRemark(null); payload.setExtra(Collections.emptyMap());
        payload.setAmount(null); payload.setDonorHash(null);
        if(payload.getFileId()!=null && StrUtil.isBlank(payload.getFileHash())) {
            List<String> hashes=db.queryForList("SELECT file_hash FROM file_record WHERE file_id=? AND deleted=0",String.class,payload.getFileId());
            if(!hashes.isEmpty()) payload.setFileHash(hashes.get(0));
        }
        try {
            String json=objectMapper.writeValueAsString(payload);
            db.update("INSERT INTO evidence_task(event_key,biz_type,biz_id,project_id,event_type,data_hash,payload,status,chain_source,create_time,update_time) VALUES(?,?,?,?,?,?,?,'PENDING',?,NOW(),NOW()) ON DUPLICATE KEY UPDATE event_key=event_key",
                    payload.getEventKey(),payload.getBizType(),payload.getBizId(),parseLong(payload.getProjectId()),payload.getEventType(),payload.getDataHash(),json,chainSource());
            Map<String,Object> t=db.queryForMap("SELECT * FROM evidence_task WHERE event_key=?",payload.getEventKey());
            if(!payload.getDataHash().equals(t.get("data_hash"))) throw new BizException(ResultCode.BAD_REQUEST,"相同事件键不能使用不同摘要");
            ChainResultDTO result=new ChainResultDTO();result.setTaskId(((Number)t.get("task_id")).longValue());
            result.setEvidenceStatus(String.valueOf(t.get("status")));result.setChainSource(String.valueOf(t.get("chain_source")));
            result.setTxId((String)t.get("tx_id"));result.setBlockHeight(t.get("block_height")==null?null:((Number)t.get("block_height")).longValue());result.setDataHash(payload.getDataHash());
            return result;
        } catch (BizException e) {throw e;}
        catch(Exception e){throw new BizException(ResultCode.CHAIN_ERROR,"创建存证任务失败");}
    }

    /** 仅由提交后的队列消费者调用，禁止业务事务直接远程上链。 */
    public ChainResultDTO submitCommitted(EvidencePayload payload) {
        // 1) 链下隐私预检：链上仅允许哈希摘要与非敏感字段
        PrivacyUtil.validate(payload);

        long start = System.currentTimeMillis();
        try {
            // 2) 调用链码存证
            EvidenceDTO existing=chainClient.queryEvent(payload.getEventKey());
            ChainResultDTO result;
            if(existing!=null) {
                if(!payload.getDataHash().equals(existing.getDataHash())) throw new BizException(ResultCode.CHAIN_ERROR,"事件键与链上摘要冲突");
                result=new ChainResultDTO();result.setTxId(existing.getTxId());result.setBlockHeight(existing.getBlockHeight());result.setTimestamp(existing.getTimestamp());result.setDataHash(existing.getDataHash());result.setChainSource(chainSource());
                result.setEvidenceNo("EV"+HashUtil.sha256Hex(existing.getTxId()).substring(0,30));
            } else result=chainClient.submitEvidence(payload);
            if (result.getBlockHeight() == null) {
                result.setBlockHeight(0L);
            }
            // 3) 保存存证凭证（ER 实体 4）
            EvidenceRecord record = saveEvidenceRecord(payload, result, CommonConst.LOG_SUCCESS, null);
            result.setEvidenceKey(record.getEvidenceNo());
            // 4) 真实 Fabric 模式同步链上快照（业务库损坏时依旧可核验）；
            //    模拟链（MockChainClient）自身已把记录写入链上快照表，无需重复写入
            if (!CommonConst.CHAIN_MOCK.equals(chainClient.source())) {
                saveSnapshot(payload, result);
            }
            log.info("存证完成 bizType={} bizId={} txId={} blockHeight={} cost={}ms",
                    payload.getBizType(), payload.getBizId(), result.getTxId(),
                    result.getBlockHeight(), System.currentTimeMillis() - start);
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("存证失败 bizType={} bizId={}", payload.getBizType(), payload.getBizId(), e);
            throw new BizException(ResultCode.CHAIN_ERROR, "联盟链存证失败：" + e.getMessage());
        }
    }

    /** 按业务查询存证凭证 */
    public EvidenceRecord findByBiz(String bizType, String bizId) {
        LambdaQueryWrapper<EvidenceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvidenceRecord::getBizType, bizType)
                .eq(EvidenceRecord::getBizId, bizId)
                .eq(EvidenceRecord::getStatus, CommonConst.LOG_SUCCESS)
                .orderByDesc(EvidenceRecord::getEvidenceId)
                .last("LIMIT 1");
        return evidenceRecordMapper.selectOne(wrapper);
    }

    public EvidenceRecord findByTxId(String txId) {
        LambdaQueryWrapper<EvidenceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvidenceRecord::getTxId, txId).last("LIMIT 1");
        return evidenceRecordMapper.selectOne(wrapper);
    }

    public List<EvidenceRecord> listByProject(Long projectId) {
        LambdaQueryWrapper<EvidenceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvidenceRecord::getProjectId, projectId)
                .eq(EvidenceRecord::getStatus, CommonConst.LOG_SUCCESS)
                .orderByAsc(EvidenceRecord::getEvidenceId);
        return evidenceRecordMapper.selectList(wrapper);
    }

    /** 哈希验真：优先向联盟链查询，链上不可达时回退到本地快照（可靠性设计） */
    public VerifyResultDTO verify(String bizType, String bizId, String hash) {
        try {
            EvidenceDTO stored=chainClient.queryEvidence(bizType,bizId);
            VerifyResultDTO r=new VerifyResultDTO();r.setBizType(bizType);r.setBizId(bizId);r.setProvidedHash(hash);r.setChainSource(chainSource());
            if(stored==null){r.setOutcome("NOT_FOUND");r.setMessage("未找到对应存证");return r;}
            r.setStoredHash(stored.getDataHash());r.setTxId(stored.getTxId());r.setBlockHeight(stored.getBlockHeight());
            r.setMatched(hash!=null&&stored.getDataHash().equalsIgnoreCase(hash));r.setOutcome(r.isMatched()?"MATCH":"MISMATCH");
            r.setMessage((r.isMatched()?"摘要一致":"摘要不一致")+("MOCK".equals(chainSource())?"（本地模拟存证，仅说明摘要比对结果，不证明材料内容真实）":"（仅说明与联盟链登记摘要的比对结果）"));return r;
        } catch (Exception e) {
            VerifyResultDTO r=new VerifyResultDTO();r.setOutcome("UNAVAILABLE");r.setChainSource(chainSource());r.setMessage("存证服务不可用，暂时无法核验");return r;
        }
    }

    /** 仅依赖链上数据的验真（业务库损坏时仍可核验） */
    public VerifyResultDTO verifyByChainOnly(String bizType, String bizId, String hash) {
        return verify(bizType,bizId,hash);
    }

    public ProjectTraceDTO projectTrace(Long projectId) {
        ProjectTraceDTO trace = chainClient.getProjectTrace(String.valueOf(projectId));
        return trace == null ? new ProjectTraceDTO() : trace;
    }

    public ChainStatsDTO chainStats() {
        return chainClient.getChainStats();
    }

    public boolean verifyChainIntegrity() {
        return chainClient.verifyChainIntegrity();
    }

    public String chaincodeVersion() {
        return chainClient.getChaincodeVersion();
    }

    public List<EvidenceDTO> queryByBlockHeight(Long blockHeight) {
        return chainClient.queryByBlockHeight(blockHeight);
    }

    public EvidenceDTO queryByTxId(String txId) {
        return chainClient.queryByTxId(txId);
    }

    public List<EvidenceDTO> queryByProjectId(Long projectId) {
        return chainClient.queryByProjectId(String.valueOf(projectId));
    }

    /** 页面展示用的“存证摘要”（链上 + 链下对照） */
    public EvidenceRecord latestByBizTypeProject(Long projectId, String bizType) {
        LambdaQueryWrapper<EvidenceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvidenceRecord::getProjectId, projectId)
                .eq(EvidenceRecord::getBizType, bizType)
                .eq(EvidenceRecord::getStatus, CommonConst.LOG_SUCCESS)
                .orderByDesc(EvidenceRecord::getEvidenceId)
                .last("LIMIT 1");
        return evidenceRecordMapper.selectOne(wrapper);
    }

    // ---------------------------------------------------------------------------------

    private EvidenceRecord saveEvidenceRecord(EvidencePayload payload, ChainResultDTO result,
                                              String status, String errorMsg) {
        if(result!=null && result.getTxId()!=null) {
            EvidenceRecord existing=findByTxId(result.getTxId());
            if(existing!=null) return existing;
        }
        EvidenceRecord record = new EvidenceRecord();
        record.setEvidenceNo(result != null && StrUtil.isNotBlank(result.getEvidenceNo())
                ? result.getEvidenceNo() : buildTempNo(payload));
        record.setTxId(result != null ? result.getTxId() : null);
        record.setBlockHeight(result != null ? result.getBlockHeight() : null);
        record.setChainTime(parseTime(result == null ? null : result.getTimestamp()));
        record.setDataHash(payload.getDataHash());
        record.setBizType(payload.getBizType());
        record.setBizId(payload.getBizId());
        record.setProjectId(parseLong(payload.getProjectId()));
        record.setFileId(payload.getFileId());
        record.setChannelName(fabricProperties.getChannelName());
        record.setChaincodeName(fabricProperties.getChaincodeName());
        record.setFunctionName(StrUtil.blankToDefault(payload.getFunctionName(), ""));
        record.setOperatorId(payload.getOperatorId());
        record.setOperatorName(payload.getOperatorName());
        record.setStatus(status);
        record.setErrorMsg(StrUtil.maxLength(errorMsg, 490));
        record.setChainSource(chainClient.source());
        record.setPayload(buildSafePayloadJson(payload));
        record.setCreateTime(LocalDateTime.now());
        evidenceRecordMapper.insert(record);
        return record;
    }

    private void saveSnapshot(EvidencePayload payload, ChainResultDTO result) {
        if(snapshotMapper.selectCount(new QueryWrapper<ChainEvidenceSnapshot>().eq("tx_id",result.getTxId()))>0)return;
        ChainEvidenceSnapshot snapshot = new ChainEvidenceSnapshot();
        snapshot.setEventKey(payload.getEventKey());
        snapshot.setTxId(result.getTxId());
        snapshot.setBlockHeight(result.getBlockHeight());
        snapshot.setBizType(payload.getBizType());
        snapshot.setBizId(payload.getBizId());
        snapshot.setProjectId(parseLong(payload.getProjectId()));
        snapshot.setDataHash(payload.getDataHash());
        snapshot.setChainTime(parseTime(result.getTimestamp()));
        snapshot.setSyncTime(LocalDateTime.now());
        snapshot.setRawPayload(buildSafePayloadJson(payload));
        snapshotMapper.insert(snapshot);
    }

    /** 构造上链摘要（仅哈希与非敏感字段，供页面展示哈希核验依据） */
    private String buildSafePayloadJson(EvidencePayload payload) {
        try {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("bizType", payload.getBizType());
            map.put("bizId", payload.getBizId());
            map.put("projectId", payload.getProjectId());
            map.put("dataHash", payload.getDataHash());
            map.put("fileHash", payload.getFileHash());
            map.put("amount", payload.getAmount());
            map.put("status", payload.getStatus());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"dataHash\":\"" + payload.getDataHash() + "\"}";
        }
    }

    private VerifyResultDTO verifyFromSnapshot(String bizType, String bizId, String hash) {
        QueryWrapper<ChainEvidenceSnapshot> wrapper = new QueryWrapper<>();
        wrapper.eq("biz_type", bizType).eq("biz_id", bizId).last("LIMIT 1");
        ChainEvidenceSnapshot snapshot = snapshotMapper.selectOne(wrapper);
        if (snapshot == null) {
            throw new BizException(ResultCode.EVIDENCE_NOT_FOUND);
        }
        VerifyResultDTO result = new VerifyResultDTO();
        result.setBizType(bizType);
        result.setBizId(bizId);
        result.setProvidedHash(hash == null ? null : hash.toLowerCase());
        result.setStoredHash(snapshot.getDataHash());
        result.setTxId(snapshot.getTxId());
        result.setBlockHeight(snapshot.getBlockHeight());
        result.setChainTime(snapshot.getChainTime() == null ? null : snapshot.getChainTime().format(TIME_FORMAT));
        result.setMatched(hash != null && snapshot.getDataHash().equalsIgnoreCase(hash));
        result.setMessage(result.isMatched() ? "哈希一致（本地快照比对，链上节点暂时不可达）" : "哈希不一致，文件可能已被篡改");
        return result;
    }

    private String buildTempNo(EvidencePayload payload) {
        return "EV-FAIL-" + HashUtil.sha256Hex(payload.getBizType() + payload.getBizId()
                + System.currentTimeMillis()).substring(0, 20).toUpperCase();
    }

    private LocalDateTime parseTime(String value) {
        if (StrUtil.isBlank(value)) {
            return LocalDateTime.now();
        }
        try {
            String normalized = value.replace("T", " ");
            if (normalized.length() > 19) {
                normalized = normalized.substring(0, 19);
            }
            return LocalDateTime.parse(normalized, TIME_FORMAT);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private Long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 业务类型中文名称（页面展示） */
    public String bizTypeLabel(String bizType) {
        return BizTypeConst.label(bizType);
    }

    public List<EvidenceRecord> listAll() {
        return evidenceRecordMapper.selectList(new LambdaQueryWrapper<EvidenceRecord>()
                .orderByDesc(EvidenceRecord::getEvidenceId));
    }

    public List<EvidenceRecord> listFailed() {
        return evidenceRecordMapper.selectList(new LambdaQueryWrapper<EvidenceRecord>()
                .eq(EvidenceRecord::getStatus, CommonConst.LOG_FAIL)
                .orderByDesc(EvidenceRecord::getEvidenceId));
    }

    public List<EvidenceDTO> emptyList() {
        return Collections.emptyList();
    }
}
