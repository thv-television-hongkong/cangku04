package com.campus.charity.fabric;

import cn.hutool.core.util.StrUtil;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.constant.BizTypeConst;
import com.campus.charity.common.constant.CommonConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.fabric.dto.ChainResultDTO;
import com.campus.charity.fabric.dto.ChainStatsDTO;
import com.campus.charity.fabric.dto.EvidenceDTO;
import com.campus.charity.fabric.dto.EvidencePayload;
import com.campus.charity.fabric.dto.ProjectTraceDTO;
import com.campus.charity.fabric.dto.VerifyResultDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.Proposal;
import org.hyperledger.fabric.client.SubmittedTransaction;
import org.hyperledger.fabric.client.Status;
import org.hyperledger.fabric.client.Transaction;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 真实 Hyperledger Fabric 联盟链客户端（fabric-gateway）。
 *
 * <p>当 {@code fabric.enabled=true} 时启用，连接 fabric-network 测试网络
 * （通道 charitychannel、链码 charity-cc），完成存证写入、多维度查询、哈希验真与全流程溯源。</p>
 *
 * <p>关键实现：Fabric 链码无法感知交易所在区块高度，因此交易提交成功后，本类从
 * {@link Status#getBlockNumber()} 读取真实区块高度，再调用链码
 * {@code IndexEvidenceBlockHeight} 建立“区块高度 -> 存证”索引，
 * 从而支持“按区块高度查询链上存证”的需求。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fabric.enabled", havingValue = "true")
public class FabricGatewayChainClient implements ChainClient {

    private final FabricProperties properties;
    private final ObjectMapper objectMapper;

    private Gateway gateway;
    private ManagedChannel channel;

    public FabricGatewayChainClient(FabricProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String source() {
        return CommonConst.CHAIN_FABRIC;
    }

    // ---------------------------------------------------------------------------------
    // 存证写入
    // ---------------------------------------------------------------------------------

    @Override
    public ChainResultDTO submitEvidence(EvidencePayload payload) {
        List<String> args = new ArrayList<>(Arrays.asList(payload.getEventKey(), payload.getBizType(),payload.getBizId(),
                StrUtil.blankToDefault(payload.getProjectId(),""),payload.getDataHash(),StrUtil.blankToDefault(payload.getFileHash(),""),
                StrUtil.blankToDefault(payload.getStatus(),"RECORDED"),StrUtil.blankToDefault(payload.getOperatorRole(),"SYSTEM")));
        String function = "AppendEvidence";
        if("REFUND".equals(payload.getBizType())) {function="CreateRefundEvidence";args.remove(1);}
        try {
            Proposal proposal = contract().newProposal(function).addArguments(args.toArray(new String[0])).build();
            Transaction transaction = proposal.endorse();
            byte[] result = transaction.getResult();
            SubmittedTransaction submitted = transaction.submitAsync();
            String txId = submitted.getTransactionId();
            Status commitStatus = submitted.getStatus();
            if(!commitStatus.isSuccessful()) throw new BizException(ResultCode.CHAIN_ERROR,"Fabric 交易未通过提交验证");
            long blockNumber = commitStatus.getBlockNumber();

            ChainResultDTO dto = objectMapper.readValue(result, ChainResultDTO.class);
            dto.setTxId(txId);
            dto.setBlockHeight(blockNumber);
            dto.setChainSource(CommonConst.CHAIN_FABRIC);

            // 回填真实区块高度索引，支持按区块高度查询链上存证
            if (blockNumber > 0) {
                try {
                    indexBlockHeight(payload.getBizType(), payload.getBizId(), blockNumber, txId);
                } catch (Exception e) {
                    log.warn("区块高度索引写入失败（不影响存证本身）: {}", e.getMessage());
                }
            }
            log.info("[Fabric] 存证上链成功 bizType={} bizId={} txId={} blockHeight={}",
                    payload.getBizType(), payload.getBizId(), txId, blockNumber);
            return dto;
        } catch (GatewayException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains("隐私校验未通过")) {
                throw new BizException(ResultCode.PRIVACY_REJECTED, message);
            }
            log.error("[Fabric] 存证交易失败: {}", message);
            throw new BizException(ResultCode.CHAIN_ERROR, "联盟链存证失败：" + message);
        } catch (Exception e) {
            log.error("[Fabric] 存证交易异常", e);
            throw new BizException(ResultCode.CHAIN_ERROR, "联盟链存证异常：" + e.getMessage());
        }
    }

    @Override
    public void indexBlockHeight(String bizType, String bizId, Long blockHeight, String txId) {
        try {
            contract().submitTransaction("IndexEvidenceBlockHeight", bizType, bizId,
                    String.valueOf(blockHeight), txId);
        } catch (Exception e) {
            throw new BizException(ResultCode.CHAIN_ERROR, "区块高度索引写入失败：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------
    // 查询 / 验真 / 溯源
    // ---------------------------------------------------------------------------------

    @Override
    public EvidenceDTO queryEvidence(String bizType, String bizId) {
        byte[] result = evaluate("QueryByBizId", bizType, bizId);
        return result == null ? null : readValue(result, EvidenceDTO.class);
    }

    @Override
    public EvidenceDTO queryEvent(String eventKey) {
        byte[] result=evaluate("QueryByEvent",eventKey);
        return result==null?null:readValue(result,EvidenceDTO.class);
    }

    @Override
    public EvidenceDTO queryByTxId(String txId) {
        byte[] result = evaluate("QueryByTxId", txId);
        return result == null ? null : readValue(result, EvidenceDTO.class);
    }

    @Override
    public List<EvidenceDTO> queryByProjectId(String projectId) {
        byte[] result = evaluate("QueryByProjectId", projectId);
        if (result == null) {
            return Collections.emptyList();
        }
        return readList(result, EvidenceDTO.class);
    }

    @Override
    public List<EvidenceDTO> queryByBlockHeight(Long blockHeight) {
        byte[] result = evaluate("QueryByBlockHeight", String.valueOf(blockHeight));
        if (result == null) {
            return Collections.emptyList();
        }
        return readList(result, EvidenceDTO.class);
    }

    @Override
    public VerifyResultDTO verifyHash(String bizType, String bizId, String hash) {
        byte[] result = evaluate("VerifyHash", bizType, bizId, hash);
        if (result == null) {
            throw new BizException(ResultCode.EVIDENCE_NOT_FOUND);
        }
        return readValue(result, VerifyResultDTO.class);
    }

    @Override
    public ProjectTraceDTO getProjectTrace(String projectId) {
        byte[] result = evaluate("GetProjectTrace", projectId);
        if (result == null) {
            return new ProjectTraceDTO();
        }
        return readValue(result, ProjectTraceDTO.class);
    }

    @Override
    public ChainStatsDTO getChainStats() {
        byte[] result = evaluate("GetChainStats");
        if (result == null) {
            return new ChainStatsDTO();
        }
        return readValue(result, ChainStatsDTO.class);
    }

    @Override
    public boolean verifyChainIntegrity() {
        byte[] result = evaluate("VerifyChainIntegrity");
        return result != null && "true".equalsIgnoreCase(new String(result, StandardCharsets.UTF_8).trim());
    }

    @Override
    public String getChaincodeVersion() {
        byte[] result = evaluate("GetChaincodeVersion");
        if (result == null) {
            return null;
        }
        String version = new String(result, StandardCharsets.UTF_8).trim();
        return version.replace("\"", "");
    }

    // ---------------------------------------------------------------------------------
    // 内部：链码调用与连接管理
    // ---------------------------------------------------------------------------------

    private byte[] evaluate(String function, String... args) {
        try {
            return contract().evaluateTransaction(function, args);
        } catch (GatewayException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains("不存在") || message.contains("NOT_FOUND")) {
                return null;
            }
            log.error("[Fabric] 查询失败 function={} error={}", function, message);
            throw new BizException(ResultCode.CHAIN_ERROR, "链上查询失败：" + message);
        }
    }

    private Contract contract() {
        return gateway().getNetwork(properties.getChannelName()).getContract(properties.getChaincodeName());
    }

    private synchronized Gateway gateway() {
        if (gateway != null) {
            return gateway;
        }
        try {
            JsonNode profile = objectMapper.readTree(new File(properties.getConnectionProfile()));
            String peerName = resolvePeerName(profile);
            JsonNode peer = profile.path("peers").path(peerName);
            String peerUrl = peer.path("url").asText();
            String tlsCertPath = peer.path("tlsCACerts").path("path").asText();
            String overrideAuthority = peer.path("grpcOptions")
                    .path("ssl-target-name-override").asText(peerName);

            SslContext sslContext = GrpcSslContexts.forClient()
                    .trustManager(new File(tlsCertPath))
                    .build();
            String target = peerUrl.replace("grpcs://", "").replace("grpc://", "");
            channel = NettyChannelBuilder.forTarget(target)
                    .sslContext(sslContext)
                    .overrideAuthority(overrideAuthority)
                    .build();

            Identity identity = new X509Identity(properties.getMspId(), readCertificate());
            Signer signer = Signers.newPrivateKeySigner(readPrivateKey());

            gateway = Gateway.newInstance()
                    .identity(identity)
                    .signer(signer)
                    .connection(channel)
                    .evaluateOptions(options -> options.withDeadlineAfter(
                            properties.getEvaluateTimeoutSeconds(), TimeUnit.SECONDS))
                    .endorseOptions(options -> options.withDeadlineAfter(
                            properties.getEndorseTimeoutSeconds(), TimeUnit.SECONDS))
                    .submitOptions(options -> options.withDeadlineAfter(
                            properties.getSubmitTimeoutSeconds(), TimeUnit.SECONDS))
                    .commitStatusOptions(options -> options.withDeadlineAfter(
                            properties.getCommitStatusTimeoutSeconds(), TimeUnit.SECONDS))
                    .connect();
            log.info("[Fabric] Gateway 连接成功 org={} channel={} chaincode={} peer={}",
                    properties.getMspId(), properties.getChannelName(),
                    properties.getChaincodeName(), peerName);
            return gateway;
        } catch (Exception e) {
            closeQuietly();
            throw new BizException(ResultCode.CHAIN_ERROR,
                    "连接 Fabric 联盟链失败（请确认 fabric-network 已启动或改用 fabric.enabled=false）：" + e.getMessage());
        }
    }

    /** 从连接配置中解析当前组织对应的 Peer 名称 */
    private String resolvePeerName(JsonNode profile) {
        JsonNode organizations = profile.path("organizations");
        java.util.Iterator<String> names = organizations.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode org = organizations.path(name);
            if (properties.getMspId().equalsIgnoreCase(org.path("mspid").asText())) {
                JsonNode peers = org.path("peers");
                if (peers.isArray() && peers.size() > 0) {
                    return peers.get(0).asText();
                }
            }
        }
        throw new BizException(ResultCode.CHAIN_ERROR, "连接配置中未找到组织 " + properties.getMspId() + " 的 Peer 节点");
    }

    private X509Certificate readCertificate() throws IOException, java.security.cert.CertificateException {
        try (BufferedReader reader = new BufferedReader(new FileReader(properties.getCertPath()))) {
            return Identities.readX509Certificate(reader);
        }
    }

    private PrivateKey readPrivateKey() throws Exception {
        Path keyDir = Paths.get(properties.getKeyPath());
        Path keyFile;
        if (Files.isDirectory(keyDir)) {
            try (Stream<Path> stream = Files.list(keyDir)) {
                keyFile = stream.filter(Files::isRegularFile).findFirst()
                        .orElseThrow(() -> new IOException("keystore 目录为空：" + keyDir));
            }
        } else {
            keyFile = keyDir;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(keyFile.toFile()))) {
            return Identities.readPrivateKey(reader);
        }
    }

    /** 组装链码参数（与 Go 链码方法签名严格一致） */
    private List<String> buildArgs(EvidencePayload p) {
        String operator = StrUtil.blankToDefault(p.getOperator(), "system");
        String role = StrUtil.blankToDefault(p.getOperatorRole(), "SYSTEM");
        switch (p.getBizType()) {
            case BizTypeConst.PROJECT:
                return Arrays.asList(p.getBizId(), p.getDataHash(),
                        StrUtil.blankToDefault(p.getStatus(), "ONLINE"), operator, role);
            case BizTypeConst.PROJECT_STATUS:
                return Arrays.asList(p.getBizId(), StrUtil.blankToDefault(p.getStatus(), "OFFLINE"),
                        p.getDataHash(), StrUtil.blankToDefault(p.getRemark(), ""), operator, role);
            case BizTypeConst.DONATION:
                return Arrays.asList(p.getBizId(), StrUtil.blankToDefault(p.getProjectId(), ""),
                        StrUtil.blankToDefault(p.getDonorHash(), p.getDataHash()),
                        StrUtil.blankToDefault(p.getAmount(), "0.00"),
                        StrUtil.blankToDefault(p.getAmountHash(), p.getDataHash()),
                        p.getDataHash(), operator, role);
            case BizTypeConst.FUND_USAGE:
                return Arrays.asList(p.getBizId(), StrUtil.blankToDefault(p.getProjectId(), ""),
                        StrUtil.blankToDefault(p.getAmount(), "0.00"),
                        StrUtil.blankToDefault(p.getFileHash(), p.getDataHash()),
                        p.getDataHash(), StrUtil.blankToDefault(p.getStatus(), "PENDING"), operator, role);
            case BizTypeConst.MATERIAL:
                return Arrays.asList(p.getBizId(), StrUtil.blankToDefault(p.getProjectId(), ""),
                        StrUtil.blankToDefault(p.getExtra().get("materialType"), "PROOF"),
                        StrUtil.blankToDefault(p.getFileHash(), p.getDataHash()),
                        p.getDataHash(), operator, role);
            case BizTypeConst.WITHDRAWAL:
                return Arrays.asList(p.getBizId(), StrUtil.blankToDefault(p.getProjectId(), ""),
                        StrUtil.blankToDefault(p.getAmount(), "0.00"), p.getDataHash(),
                        StrUtil.blankToDefault(p.getStatus(), "APPLIED"), operator, role);
            case BizTypeConst.PROJECT_PROGRESS:
                return Arrays.asList(p.getBizId(), StrUtil.blankToDefault(p.getProjectId(), ""),
                        StrUtil.blankToDefault(p.getFileHash(), p.getDataHash()), p.getDataHash(),
                        StrUtil.blankToDefault(p.getStatus(), "0"), operator, role);
            case BizTypeConst.AUDIT_REPORT:
                return Arrays.asList(p.getBizId(), StrUtil.blankToDefault(p.getProjectId(), ""),
                        StrUtil.blankToDefault(p.getStatus(), "COMPREHENSIVE"),
                        StrUtil.blankToDefault(p.getFileHash(), p.getDataHash()),
                        p.getDataHash(), operator, role);
            default:
                throw new BizException(ResultCode.CHAIN_ERROR, "不支持的存证业务类型：" + p.getBizType());
        }
    }

    private String resolveFunction(String bizType) {
        if (BizTypeConst.PROJECT.equals(bizType)) {
            return "CreateProjectEvidence";
        }
        if (BizTypeConst.PROJECT_STATUS.equals(bizType)) {
            return "UpdateProjectStatus";
        }
        if (BizTypeConst.DONATION.equals(bizType)) {
            return "CreateDonationEvidence";
        }
        if (BizTypeConst.FUND_USAGE.equals(bizType)) {
            return "CreateFundUsageEvidence";
        }
        if (BizTypeConst.MATERIAL.equals(bizType)) {
            return "CreateMaterialEvidence";
        }
        if (BizTypeConst.WITHDRAWAL.equals(bizType)) {
            return "CreateWithdrawalEvidence";
        }
        if (BizTypeConst.PROJECT_PROGRESS.equals(bizType)) {
            return "CreateProjectProgressEvidence";
        }
        if (BizTypeConst.AUDIT_REPORT.equals(bizType)) {
            return "CreateReportEvidence";
        }
        throw new BizException(ResultCode.CHAIN_ERROR, "不支持的存证业务类型：" + bizType);
    }

    private <T> T readValue(byte[] result, Class<T> type) {
        try {
            return objectMapper.readValue(result, type);
        } catch (IOException e) {
            throw new BizException(ResultCode.CHAIN_ERROR, "解析链上返回数据失败：" + e.getMessage());
        }
    }

    private <T> List<T> readList(byte[] result, Class<T> type) {
        try {
            return objectMapper.readValue(result,
                    objectMapper.getTypeFactory().constructCollectionType(ArrayList.class, type));
        } catch (IOException e) {
            throw new BizException(ResultCode.CHAIN_ERROR, "解析链上返回列表失败：" + e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (gateway != null) {
                gateway.close();
            }
        } catch (Exception ignored) {
            // ignore
        }
        try {
            if (channel != null && !channel.isShutdown()) {
                channel.shutdownNow();
            }
        } catch (Exception ignored) {
            // ignore
        }
        gateway = null;
        channel = null;
    }

    /** 便于监控：返回链上节点列表（管理员后台“区块链运行状态”） */
    public List<String> connectedPeers() {
        try {
            JsonNode profile = objectMapper.readTree(new File(properties.getConnectionProfile()));
            JsonNode peers = profile.path("peers");
            List<String> names = new ArrayList<>();
            peers.fieldNames().forEachRemaining(names::add);
            return names.stream().map(n -> "已配置（未探测）: " + n).collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
