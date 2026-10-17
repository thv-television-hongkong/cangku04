package com.campus.charity.fabric;

import com.campus.charity.fabric.dto.ChainResultDTO;
import com.campus.charity.fabric.dto.ChainStatsDTO;
import com.campus.charity.fabric.dto.EvidenceDTO;
import com.campus.charity.fabric.dto.EvidencePayload;
import com.campus.charity.fabric.dto.ProjectTraceDTO;
import com.campus.charity.fabric.dto.VerifyResultDTO;

import java.util.List;

/**
 * 联盟链存证客户端抽象。
 *
 * <p>两种实现：</p>
 * <ul>
 *   <li>{@link FabricGatewayChainClient}：真实 Hyperledger Fabric 联盟链（fabric-gateway）</li>
 *   <li>{@link MockChainClient}：内置模拟联盟链（无 Docker 环境下开发联调，接口行为一致）</li>
 * </ul>
 */
public interface ChainClient {

    /** 存证来源标识：FABRIC / MOCK */
    String source();

    /** 写入存证，返回交易ID、区块高度等凭证信息 */
    ChainResultDTO submitEvidence(EvidencePayload payload);

    /** 回填真实区块高度索引（支持按区块高度查询） */
    void indexBlockHeight(String bizType, String bizId, Long blockHeight, String txId);

    /** 按业务类型 + 业务ID 查询存证 */
    EvidenceDTO queryEvidence(String bizType, String bizId);

    /** 精确查询不可变事件，用于不确定提交结果后的补偿。 */
    EvidenceDTO queryEvent(String eventKey);

    /** 按交易ID 查询存证 */
    EvidenceDTO queryByTxId(String txId);

    /** 按项目ID 查询全部存证（项目全流程溯源） */
    List<EvidenceDTO> queryByProjectId(String projectId);

    /** 按区块高度查询存证 */
    List<EvidenceDTO> queryByBlockHeight(Long blockHeight);

    /** 哈希验真 */
    VerifyResultDTO verifyHash(String bizType, String bizId, String hash);

    /** 项目全流程溯源（项目 + 捐赠 + 资金提取 + 资金凭证 + 帮扶材料） */
    ProjectTraceDTO getProjectTrace(String projectId);

    /** 链上统计 */
    ChainStatsDTO getChainStats();

    /** 链式完整性校验 */
    boolean verifyChainIntegrity();

    /** 链码版本（管理员合约升级后可校验） */
    String getChaincodeVersion();
}
