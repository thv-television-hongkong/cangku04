package com.campus.charity.module.project.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 众筹项目模块 DTO / VO 集合。
 */
public class ProjectDtos {

    /** 发布 / 编辑项目（include 设置筹款目标） */
    @Data
    public static class ProjectForm implements Serializable {
        private Long projectId;
        private String title;
        private String category;
        /** 帮扶对象姓名（仅社团与管理员可见，入库时自动生成脱敏展示名） */
        private String beneficiary;
        private String beneficiaryContact;
        private String beneficiaryDesc;
        private BigDecimal targetAmount;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime endTime;
        private String description;
        private String fundsPlan;
        private String coverImage;
        /** 是否直接提交审核（false 则保存为草稿） */
        private Boolean submit = Boolean.TRUE;
    }

    /** 项目查询 */
    @Data
    public static class ProjectQuery implements Serializable {
        private String keyword;
        private String category;
        private String status;
        private Long clubId;
        private Long donorId;
        /** 排序：latest 最新 / hot 热度 / progress 进度 / amount 金额 */
        private String sort = "latest";
        private long current = 1;
        private long size = 9;
    }

    /** 项目审核（管理员：include 项目合规审查） */
    @Data
    public static class ProjectAuditForm implements Serializable {
        private Long projectId;
        /** PASS 通过 / REJECT 驳回 */
        private String result;
        private String opinion;
        /** 合规审查项：真实性 / 合规性 / 材料完整性 / 预算合理性 / 帮扶对象真实性 */
        private Map<String, String> complianceItems;
    }

    /** 下架违规项目（extends） */
    @Data
    public static class OfflineForm implements Serializable {
        private Long projectId;
        private String reason;
    }

    /** 项目证明材料提交 */
    @Data
    public static class MaterialForm implements Serializable {
        private Long projectId;
        /** PROJECT_PLAN / PROOF / BENEFICIARY / OTHER */
        private String materialType = "PROOF";
        private String materialName;
        private String remark;
        private Long fileId;
        private String fileName;
        private String fileUrl;
        private String fileHash;
        private Long fileSize;
    }

    /** 项目进度（编辑项目 include 更新项目进度） */
    @Data
    public static class ProgressForm implements Serializable {
        private Long projectId;
        private String title;
        private String content;
        private Integer progressPercent;
        private Long fileId;
        private String fileHash;
    }

    /** 项目评价 */
    @Data
    public static class ReviewForm implements Serializable {
        private Long projectId;
        private Long donationId;
        private Integer rating = 5;
        private String content;
        private Integer anonymous = 0;
    }

    /** 评价回复 */
    @Data
    public static class ReviewReplyForm implements Serializable {
        private Long reviewId;
        private String replyContent;
    }

    /** 项目列表项（对外展示，帮扶对象一律脱敏） */
    @Data
    public static class ProjectVO implements Serializable {
        private Long projectId;
        private String projectNo;
        private String title;
        private String category;
        private String categoryLabel;
        /** 脱敏展示的帮扶对象 */
        private String beneficiaryMasked;
        private BigDecimal targetAmount;
        private BigDecimal currentAmount;
        private Integer fundingPercent;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String status;
        private String statusLabel;
        private Long clubId;
        private String clubName;
        private String description;
        private String coverImage;
        private Integer donorCount;
        private Integer viewCount;
        private Integer favoriteCount;
        private Integer shareCount;
        private Integer progressPercent;
        /** 链上存证信息（浏览列表即展示，保障真实性） */
        private String dataHash;
        private String txId;
        private Long blockHeight;
        private String chainSource;
        private LocalDateTime auditTime;
        private String auditOpinion;
        private Integer remainingDays;
    }

    /** 项目详情 */
    @Data
    public static class ProjectDetailVO extends ProjectVO implements Serializable {
        private String beneficiaryDesc;
        private String beneficiaryContactMasked;
        private String fundsPlan;
        private Boolean favorited;
        private String myDonationAmount;
        private Map<String, Object> fundFlow;
        private Map<String, Object> ratingSummary;
        private List<Map<String, Object>> fundUsageList = new ArrayList<>();
        private List<Map<String, Object>> progressList = new ArrayList<>();
        private List<Map<String, Object>> materialList = new ArrayList<>();
        private List<Map<String, Object>> auditList = new ArrayList<>();
        private List<Map<String, Object>> reviewList = new ArrayList<>();
        private List<Map<String, Object>> evidenceList = new ArrayList<>();
    }

    private ProjectDtos() {
    }
}
