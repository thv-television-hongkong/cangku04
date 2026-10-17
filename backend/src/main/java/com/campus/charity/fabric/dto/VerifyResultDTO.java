package com.campus.charity.fabric.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 哈希验真结果。
 */
@Data
public class VerifyResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean matched;
    /** MATCH / MISMATCH / NOT_FOUND / UNAVAILABLE */
    private String outcome;
    private String chainSource;
    private String bizType;
    private String bizId;
    private String providedHash;
    private String storedHash;
    private String txId;
    private Long blockHeight;
    private String chainTime;
    private Long chainSeq;
    private String message;
}
