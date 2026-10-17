package com.campus.charity.module.trace.service;

import com.campus.charity.module.trace.dto.TraceDtos;

import java.util.List;
import java.util.Map;

/**
 * 溯源查询服务：项目 ID / 交易 ID / 区块高度多维度查询链上存证，项目全流程溯源，哈希校验。
 */
public interface TraceService {

    /** 多维溯源查询 */
    TraceDtos.TraceResult query(TraceDtos.TraceQuery query);

    /** 哈希验真（支持直接传文件哈希） */
    TraceDtos.VerifyResult verify(TraceDtos.VerifyForm form);

    /** 仅依据链上数据验真（业务库损坏时仍可核验） */
    TraceDtos.VerifyResult verifyByChainOnly(TraceDtos.VerifyForm form);

    /** 项目全流程溯源（链上时间线 + 链下业务时间线） */
    TraceDtos.TraceResult projectTrace(Long projectId);

    /** 根据文件ID校验其哈希与链上存证是否一致 */
    TraceDtos.VerifyResult verifyFile(Long fileId);

    /** 导出存证数据（监管方：include 导出存证数据） */
    List<Map<String, Object>> exportEvidence(TraceDtos.TraceQuery query);

    /** 链上存证总览 */
    Map<String, Object> chainOverview();
}
