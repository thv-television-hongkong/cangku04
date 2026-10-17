package com.campus.charity.module.file.service;

import com.campus.charity.module.file.dto.FileDtos;
import com.campus.charity.module.file.entity.FileRecord;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

/**
 * 文件服务。
 *
 * <p>法律约束：原始文件保存在业务服务器本地目录（可扩展 OSS/MinIO），
 * 仅将文件 SHA-256 哈希摘要上链存证。</p>
 */
public interface FileService {

    /** 上传原始文件（计算 SHA-256，写入 file_record，返回哈希与下载地址） */
    FileDtos.FileVO upload(MultipartFile file, String bizType, String bizId);

    /** 下载原始文件 */
    void download(Long fileId, HttpServletResponse response);

    FileRecord getById(Long fileId);

    /** 文件上链后回写交易ID与区块高度 */
    void markOnChain(Long fileId, String txId, Long blockHeight);

    /** 哈希比对：校验文件是否被篡改 */
    FileDtos.FileVerifyVO verify(Long fileId, MultipartFile file);

    /** 基于文件ID重新计算哈希并与存证比对 */
    FileDtos.FileVerifyVO verifyByStoredFile(Long fileId);
}
