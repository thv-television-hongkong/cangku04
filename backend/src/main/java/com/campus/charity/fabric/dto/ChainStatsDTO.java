package com.campus.charity.fabric.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 链上存证统计（审计概览 / 导出存证数据）。
 */
@Data
public class ChainStatsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long totalEvidence = 0L;
    private Map<String, Long> byBizType = new LinkedHashMap<>();
    private Long projectCount = 0L;
    private Long latestSeq = 0L;
    private String latestHash;
    private String chaincodeVersion;
}
