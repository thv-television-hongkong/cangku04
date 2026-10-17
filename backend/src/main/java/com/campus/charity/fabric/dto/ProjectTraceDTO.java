package com.campus.charity.fabric.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目全流程溯源结果（链上）。
 */
@Data
public class ProjectTraceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String projectId;
    private Integer evidenceCount = 0;
    private Integer donationCount = 0;
    private Integer fundUsageCount = 0;
    private Integer materialCount = 0;
    private Integer withdrawalCount = 0;
    private String donationTotal;
    private String withdrawalTotal;
    private String fundUsageTotal;
    private Long latestBlockHeight;
    private String latestChainHash;
    private Boolean chainIntact;
    private List<EvidenceDTO> timeline = new ArrayList<>();
}
