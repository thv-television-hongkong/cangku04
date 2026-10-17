package com.campus.charity.module.donation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 捐赠业务模块 DTO / VO 集合。
 */
public class DonationDtos {

    /** 发起捐赠 */
    @Data
    public static class DonateForm implements Serializable {
        private Long projectId;
        private BigDecimal amount;
        /** CAMPUS_CARD / ALIPAY / WECHAT / OFFLINE */
        private String payChannel = "CAMPUS_CARD";
        private Integer anonymous = 0;
        private String remark;
    }

    /** 取消捐赠（extends：限定时间内取消捐赠） */
    @Data
    public static class CancelForm implements Serializable {
        private Long donationId;
        private String reason;
    }

    /** 捐赠记录查询 */
    @Data
    public static class DonationQuery implements Serializable {
        private Long projectId;
        private Long donorId;
        private String status;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime endTime;
        /** mine 我的捐赠 / project 项目捐赠明细 */
        private String scope = "mine";
        private long current = 1;
        private long size = 10;
    }

    /** 捐赠记录 VO（含存证凭证与项目反馈） */
    @Data
    public static class DonationVO implements Serializable {
        private Long orderId;
        private Long confirmationId;
        private String refundStatus;
        private Boolean refundEligible;
        private String refundUnavailableReason;
        private String evidenceStatus;
        private Long donationId;
        private String donationNo;
        private Long projectId;
        private String projectTitle;
        private String projectStatus;
        private Long donorId;
        private String donorName;
        private BigDecimal amount;
        private LocalDateTime donateTime;
        private String payChannel;
        private String status;
        private String statusLabel;
        private LocalDateTime cancelDeadline;
        private Boolean cancellable;
        private Integer anonymous;
        private String remark;
        /** 存证凭证：交易 ID + 区块高度 + 存证哈希 */
        private String txId;
        private Long blockHeight;
        private String dataHash;
        private LocalDateTime chainTime;
        private String chainSource;
        /** 项目最新反馈（include 查看项目反馈） */
        private String projectFeedback;
        private LocalDateTime projectProgressTime;
    }

    private DonationDtos() {
    }
}
