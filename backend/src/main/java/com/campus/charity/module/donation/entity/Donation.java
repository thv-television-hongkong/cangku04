package com.campus.charity.module.donation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 捐赠记录（ER 实体 3）。
 *
 * <p>捐赠ID / 项目ID / 捐赠人ID / 捐赠金额 / 捐赠时间 / 交易ID。</p>
 */
@Data
@TableName("donation")
public class Donation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long donationId;
    private Long orderId;
    private Long confirmationId;
    private Long confirmUserId;
    private LocalDateTime confirmTime;
    private String refundStatus;
    private String donationNo;
    private Long projectId;
    private Long donorId;
    private BigDecimal amount;
    private LocalDateTime donateTime;
    /** CAMPUS_CARD / ALIPAY / WECHAT / OFFLINE */
    private String payChannel;
    /** PAID / CANCELLED / CONFIRMED */
    private String status;
    /** 可取消截止时间（限定时间内取消捐赠） */
    private LocalDateTime cancelDeadline;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private Integer anonymous;
    private String remark;
    /** 捐赠摘要哈希（上链内容） */
    private String dataHash;
    private String txId;
    private Long blockHeight;
    private Long evidenceId;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public boolean isCancelled() {
        return "CANCELLED".equals(status);
    }
}
