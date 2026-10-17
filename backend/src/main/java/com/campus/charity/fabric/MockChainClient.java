package com.campus.charity.fabric;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.constant.BizTypeConst;
import com.campus.charity.common.constant.CommonConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.HashUtil;
import com.campus.charity.fabric.dto.ChainResultDTO;
import com.campus.charity.fabric.dto.ChainStatsDTO;
import com.campus.charity.fabric.dto.EvidenceDTO;
import com.campus.charity.fabric.dto.EvidencePayload;
import com.campus.charity.fabric.dto.ProjectTraceDTO;
import com.campus.charity.fabric.dto.VerifyResultDTO;
import com.campus.charity.module.evidence.entity.ChainEvidenceSnapshot;
import com.campus.charity.module.evidence.mapper.ChainEvidenceSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 内置模拟联盟链客户端。
 *
 * <p>当 {@code fabric.enabled=false} 时启用，用本地链式结构（前序哈希串联 + 快照表存储）
 * 模拟 Fabric 联盟链行为：同样返回交易ID、区块高度、链式哈希，同样支持多维度查询、
 * 哈希验真与全流程溯源，便于无 Docker 环境下完成全部业务联调与演示。</p>
 *
 * <p>切换到真实链只需将 {@code fabric.enabled} 置为 true，业务代码无需任何修改。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fabric.enabled", havingValue = "false", matchIfMissing = true)
public class MockChainClient implements ChainClient {

    private static final String GENESIS = "0000000000000000000000000000000000000000000000000000000000000000";

    private final ChainEvidenceSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    public MockChainClient(ChainEvidenceSnapshotMapper snapshotMapper, ObjectMapper objectMapper) {
        this.snapshotMapper = snapshotMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String source() {
        return CommonConst.CHAIN_MOCK;
    }

    @Override
    public synchronized ChainResultDTO submitEvidence(EvidencePayload payload) {
        try {
            // 幂等：同一业务 + 同一哈希不重复上链
            ChainEvidenceSnapshot exist = snapshotMapper.selectOne(new QueryWrapper<ChainEvidenceSnapshot>().eq("event_key",payload.getEventKey()).last("LIMIT 1"));
            if (exist != null) {
                if (!exist.getDataHash().equals(payload.getDataHash())) throw new BizException(ResultCode.BAD_REQUEST,"事件键已存在且哈希不同");
                return toResult(exist,payload);
            }

            long blockHeight = nextBlockHeight();
            long chainSeq = blockHeight;
            String txId = "mocktx" + UUID.randomUUID().toString().replace("-", "");
            LocalDateTime now = LocalDateTime.now();
            String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            String prevHash = latestChainHash();
            String key = "EV#" + payload.getBizType() + "#" + payload.getBizId();
            String chainHash = HashUtil.sha256Hex(prevHash + "|" + key + "|" + payload.getDataHash()
                    + "|" + txId + "|" + timestamp + "|" + chainSeq);

            ChainEvidenceSnapshot snapshot = new ChainEvidenceSnapshot();
            snapshot.setTxId(txId);
            snapshot.setEventKey(payload.getEventKey());
            snapshot.setBlockHeight(blockHeight);
            snapshot.setBizType(payload.getBizType());
            snapshot.setBizId(payload.getBizId());
            snapshot.setProjectId(parseLong(payload.getProjectId()));
            snapshot.setDataHash(payload.getDataHash());
            snapshot.setChainTime(now);
            snapshot.setSyncTime(now);
            snapshot.setRawPayload(objectMapper.writeValueAsString(buildEvidence(payload, txId, chainSeq,
                    blockHeight, timestamp, prevHash, chainHash)));
            snapshotMapper.insert(snapshot);

            log.info("[模拟链] 存证上链成功 bizType={} bizId={} txId={} blockHeight={}",
                    payload.getBizType(), payload.getBizId(), txId, blockHeight);
            return toResult(snapshot, payload);
        } catch (Exception e) {
            log.error("[模拟链] 存证上链失败", e);
            throw new BizException(ResultCode.CHAIN_ERROR, "模拟联盟链存证失败：" + e.getMessage());
        }
    }

    @Override
    public void indexBlockHeight(String bizType, String bizId, Long blockHeight, String txId) {
        // 模拟链在写入时已确定区块高度，此处无需额外操作
        log.debug("[模拟链] 区块高度已就绪 bizType={} bizId={} height={}", bizType, bizId, blockHeight);
    }

    @Override
    public EvidenceDTO queryEvidence(String bizType, String bizId) {
        ChainEvidenceSnapshot snapshot = findByBiz(bizType, bizId);
        return snapshot == null ? null : parseEvidence(snapshot);
    }

    @Override
    public EvidenceDTO queryEvent(String eventKey) {
        ChainEvidenceSnapshot s=snapshotMapper.selectOne(new QueryWrapper<ChainEvidenceSnapshot>().eq("event_key",eventKey).last("LIMIT 1"));
        return s==null?null:parseEvidence(s);
    }

    @Override
    public EvidenceDTO queryByTxId(String txId) {
        QueryWrapper<ChainEvidenceSnapshot> wrapper = new QueryWrapper<>();
        wrapper.eq("tx_id", txId).last("LIMIT 1");
        ChainEvidenceSnapshot snapshot = snapshotMapper.selectOne(wrapper);
        return snapshot == null ? null : parseEvidence(snapshot);
    }

    @Override
    public List<EvidenceDTO> queryByProjectId(String projectId) {
        QueryWrapper<ChainEvidenceSnapshot> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", parseLong(projectId)).orderByAsc("block_height");
        return snapshotMapper.selectList(wrapper).stream().map(this::parseEvidence).collect(Collectors.toList());
    }

    @Override
    public List<EvidenceDTO> queryByBlockHeight(Long blockHeight) {
        QueryWrapper<ChainEvidenceSnapshot> wrapper = new QueryWrapper<>();
        wrapper.eq("block_height", blockHeight);
        return snapshotMapper.selectList(wrapper).stream().map(this::parseEvidence).collect(Collectors.toList());
    }

    @Override
    public VerifyResultDTO verifyHash(String bizType, String bizId, String hash) {
        ChainEvidenceSnapshot snapshot = findByBiz(bizType, bizId);
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
        result.setChainSeq(snapshot.getBlockHeight());
        result.setChainTime(snapshot.getChainTime() == null ? null
                : snapshot.getChainTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.setMatched(hash != null && snapshot.getDataHash().equalsIgnoreCase(hash));
        result.setMessage(result.isMatched() ? "哈希一致，文件未被篡改" : "哈希不一致，文件可能已被篡改");
        return result;
    }

    @Override
    public ProjectTraceDTO getProjectTrace(String projectId) {
        List<EvidenceDTO> list = queryByProjectId(projectId);
        ProjectTraceDTO trace = new ProjectTraceDTO();
        trace.setProjectId(projectId);
        trace.setTimeline(list);
        trace.setEvidenceCount(list.size());
        BigDecimal donationTotal = BigDecimal.ZERO;
        BigDecimal withdrawalTotal = BigDecimal.ZERO;
        BigDecimal usageTotal = BigDecimal.ZERO;
        long latestBlock = 0L;
        for (EvidenceDTO e : list) {
            if (e.getBlockHeight() != null && e.getBlockHeight() > latestBlock) {
                latestBlock = e.getBlockHeight();
            }
            if (BizTypeConst.DONATION.equals(e.getBizType())) {
                trace.setDonationCount(trace.getDonationCount() + 1);
                donationTotal = donationTotal.add(parseAmount(e.getAmount()));
            } else if (BizTypeConst.FUND_USAGE.equals(e.getBizType())) {
                trace.setFundUsageCount(trace.getFundUsageCount() + 1);
                usageTotal = usageTotal.add(parseAmount(e.getAmount()));
            } else if (BizTypeConst.WITHDRAWAL.equals(e.getBizType())) {
                trace.setWithdrawalCount(trace.getWithdrawalCount() + 1);
                withdrawalTotal = withdrawalTotal.add(parseAmount(e.getAmount()));
            } else if (BizTypeConst.MATERIAL.equals(e.getBizType())) {
                trace.setMaterialCount(trace.getMaterialCount() + 1);
            }
        }
        trace.setLatestBlockHeight(latestBlock);
        trace.setLatestChainHash(list.isEmpty() ? null : list.get(list.size() - 1).getChainHash());
        trace.setDonationTotal(donationTotal.toPlainString());
        trace.setWithdrawalTotal(withdrawalTotal.toPlainString());
        trace.setFundUsageTotal(usageTotal.toPlainString());
        trace.setChainIntact(verifyChainIntegrity());
        return trace;
    }

    @Override
    public ChainStatsDTO getChainStats() {
        List<ChainEvidenceSnapshot> all = snapshotMapper.selectList(new QueryWrapper<>());
        ChainStatsDTO stats = new ChainStatsDTO();
        stats.setTotalEvidence((long) all.size());
        Map<String, Long> byType = new LinkedHashMap<>();
        for (ChainEvidenceSnapshot s : all) {
            byType.merge(s.getBizType(), 1L, Long::sum);
        }
        stats.setByBizType(byType);
        stats.setProjectCount(all.stream().map(ChainEvidenceSnapshot::getProjectId)
                .filter(java.util.Objects::nonNull).distinct().count());
        stats.setLatestSeq(all.stream().map(ChainEvidenceSnapshot::getBlockHeight)
                .filter(java.util.Objects::nonNull).max(Long::compareTo).orElse(0L));
        if (!all.isEmpty()) {
            ChainEvidenceSnapshot latest = all.stream()
                    .max(Comparator.comparing(ChainEvidenceSnapshot::getBlockHeight)).orElse(null);
            if (latest != null) {
                EvidenceDTO dto = parseEvidence(latest);
                stats.setLatestHash(dto.getChainHash());
            }
        }
        stats.setChaincodeVersion(getChaincodeVersion());
        return stats;
    }

    @Override
    public boolean verifyChainIntegrity() {
        List<ChainEvidenceSnapshot> all = snapshotMapper.selectList(new QueryWrapper<>());
        all.sort(Comparator.comparing(ChainEvidenceSnapshot::getBlockHeight));
        String prev = null;
        for (ChainEvidenceSnapshot s : all) {
            EvidenceDTO dto = parseEvidence(s);
            if (prev != null && !prev.equals(dto.getPrevChainHash())) {
                return false;
            }
            String recomputed=HashUtil.sha256Hex(dto.getPrevChainHash()+"|"+dto.getKey()+"|"+dto.getDataHash()+"|"+dto.getTxId()+"|"+dto.getTimestamp()+"|"+dto.getChainSeq());
            if(!recomputed.equals(dto.getChainHash()) || !s.getDataHash().equals(dto.getDataHash())) return false;
            prev = dto.getChainHash();
        }
        return true;
    }

    @Override
    public String getChaincodeVersion() {
        return "2.0-mock";
    }

    // ---------------------------------------------------------------------------------

    private ChainEvidenceSnapshot findByBiz(String bizType, String bizId) {
        LambdaQueryWrapper<ChainEvidenceSnapshot> wrapper = new LambdaQueryWrapper<>();
        // 同一业务键可能存在多次存证（如捐赠取消），取最新的链上记录
        wrapper.eq(ChainEvidenceSnapshot::getBizType, bizType)
                .eq(ChainEvidenceSnapshot::getBizId, bizId)
                .orderByDesc(ChainEvidenceSnapshot::getBlockHeight)
                .last("LIMIT 1");
        return snapshotMapper.selectOne(wrapper);
    }

    private long nextBlockHeight() {
        ChainEvidenceSnapshot last = snapshotMapper.selectOne(new QueryWrapper<ChainEvidenceSnapshot>()
                .orderByDesc("block_height").last("LIMIT 1"));
        return (last == null || last.getBlockHeight() == null ? 100L : last.getBlockHeight()) + 1L;
    }

    private String latestChainHash() {
        ChainEvidenceSnapshot last = snapshotMapper.selectOne(new QueryWrapper<ChainEvidenceSnapshot>()
                .orderByDesc("block_height").last("LIMIT 1"));
        if (last == null || StrUtil.isBlank(last.getRawPayload())) {
            return GENESIS;
        }
        EvidenceDTO dto = parseEvidence(last);
        return dto.getChainHash() == null ? GENESIS : dto.getChainHash();
    }

    private EvidenceDTO buildEvidence(EvidencePayload p, String txId, long chainSeq, long blockHeight,
                                      String timestamp, String prevHash, String chainHash) {
        EvidenceDTO dto = new EvidenceDTO();
        dto.setKey("EV#" + p.getBizType() + "#" + p.getBizId());
        dto.setEventKey(p.getEventKey());
        dto.setBizType(p.getBizType());
        dto.setBizId(p.getBizId());
        dto.setProjectId(p.getProjectId());
        dto.setDataHash(p.getDataHash());
        dto.setFileHash(p.getFileHash());
        dto.setAmount(p.getAmount());
        dto.setAmountHash(p.getAmountHash());
        dto.setDonorHash(p.getDonorHash());
        dto.setOperator(p.getOperator());
        dto.setOperatorRole(p.getOperatorRole());
        dto.setStatus(p.getStatus());
        dto.setRemark(p.getRemark());
        dto.setTxId(txId);
        dto.setChainSeq(chainSeq);
        dto.setBlockHeight(blockHeight);
        dto.setTimestamp(timestamp);
        dto.setPrevChainHash(prevHash);
        dto.setChainHash(chainHash);
        dto.setVersion(getChaincodeVersion());
        return dto;
    }

    private EvidenceDTO parseEvidence(ChainEvidenceSnapshot snapshot) {
        try {
            return objectMapper.readValue(snapshot.getRawPayload(), EvidenceDTO.class);
        } catch (Exception e) {
            EvidenceDTO dto = new EvidenceDTO();
            dto.setBizType(snapshot.getBizType());
            dto.setBizId(snapshot.getBizId());
            dto.setProjectId(snapshot.getProjectId() == null ? null : String.valueOf(snapshot.getProjectId()));
            dto.setDataHash(snapshot.getDataHash());
            dto.setTxId(snapshot.getTxId());
            dto.setBlockHeight(snapshot.getBlockHeight());
            return dto;
        }
    }

    private ChainResultDTO toResult(ChainEvidenceSnapshot snapshot, EvidencePayload payload) {
        EvidenceDTO dto = parseEvidence(snapshot);
        ChainResultDTO result = new ChainResultDTO();
        result.setTxId(snapshot.getTxId());
        result.setBlockHeight(snapshot.getBlockHeight());
        result.setChainSeq(dto.getChainSeq());
        result.setTimestamp(dto.getTimestamp());
        result.setDataHash(snapshot.getDataHash());
        result.setChainHash(dto.getChainHash());
        result.setEvidenceKey(dto.getKey());
        result.setEvidenceNo("EV" + String.format("%010d", dto.getChainSeq() == null ? 0 : dto.getChainSeq()));
        result.setBizType(payload.getBizType());
        result.setBizId(payload.getBizId());
        result.setProjectId(payload.getProjectId());
        result.setStatus(payload.getStatus());
        result.setChainSource(CommonConst.CHAIN_MOCK);
        return result;
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

    private BigDecimal parseAmount(String value) {
        if (StrUtil.isBlank(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** 供管理端查看模拟链节点信息 */
    public List<Map<String, Object>> mockNodes() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(node("peer0.org1.campus.edu", "Org1MSP", "模拟节点（本地）", true));
        nodes.add(node("peer1.org1.campus.edu", "Org1MSP", "模拟节点（本地）", true));
        nodes.add(node("peer0.org2.campus.edu", "Org2MSP", "模拟节点（本地）", true));
        nodes.add(node("peer1.org2.campus.edu", "Org2MSP", "模拟节点（本地）", true));
        return nodes;
    }

    private Map<String, Object> node(String name, String msp, String type, boolean online) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("mspId", msp);
        map.put("type", type);
        map.put("online", online);
        return map;
    }
}
