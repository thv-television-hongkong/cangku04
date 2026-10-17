package com.campus.charity.module.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 原始文件索引。
 *
 * <p>法律约束：原始文件存储在业务服务器（file_path / file_url），只有 file_hash 会上链存证。</p>
 */
@Data
@TableName("file_record")
public class FileRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long fileId;
    private String fileNo;
    private String originalName;
    private String storedName;
    /** 业务服务器存储绝对路径 */
    private String filePath;
    private String fileUrl;
    /** 文件 SHA-256 哈希（上链内容） */
    private String fileHash;
    private Long fileSize;
    private String contentType;
    /** PROJECT_MATERIAL / FUND_USAGE / PROJECT_COVER / QUALIFICATION / AUDIT_REPORT / OTHER */
    private String bizType;
    private String bizId;
    private Long uploaderId;
    private Integer onChain;
    private String txId;
    private Long blockHeight;
    /** 0未核验 1一致 2不一致 */
    private Integer hashVerified;
    private LocalDateTime verifyTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
