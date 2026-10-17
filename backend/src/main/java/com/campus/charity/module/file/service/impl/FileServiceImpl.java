package com.campus.charity.module.file.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.config.FileProperties;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.HashUtil;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.module.file.dto.FileDtos;
import com.campus.charity.module.file.entity.FileRecord;
import com.campus.charity.module.file.mapper.FileRecordMapper;
import com.campus.charity.module.file.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.campus.charity.common.constant.ProjectStatusConst;
import com.campus.charity.module.project.entity.Project;
import com.campus.charity.module.project.mapper.ProjectMapper;

/**
 * 文件服务实现：原始文件存业务服务器，仅哈希上链。
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FileRecordMapper fileRecordMapper;
    private final FileProperties fileProperties;
    private final ProjectMapper projectMapper;

    public FileServiceImpl(FileRecordMapper fileRecordMapper, FileProperties fileProperties,
                           ProjectMapper projectMapper) {
        this.fileRecordMapper = fileRecordMapper;
        this.fileProperties = fileProperties;
        this.projectMapper = projectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileDtos.FileVO upload(MultipartFile file, String bizType, String bizId) {
        if (!(SecurityUtil.isClub() || SecurityUtil.isAdmin() || SecurityUtil.isAuditor() || (SecurityUtil.isStudent() && ("PAYMENT_VOUCHER".equals(bizType) || "IDENTITY".equals(bizType))))) {
            throw new BizException(ResultCode.FORBIDDEN, "当前角色不能上传业务文件");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.FILE_ERROR, "上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String extension = FileUtil.extName(originalName);
        if (!fileProperties.isAllowed(extension)) {
            throw new BizException(ResultCode.FILE_ERROR,
                    "不支持的文件类型：" + extension + "，允许：" + fileProperties.getAllowedExtensions());
        }
        long maxBytes = fileProperties.getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BizException(ResultCode.FILE_ERROR, "文件超过大小限制：" + fileProperties.getMaxSizeMb() + "MB");
        }

        String day = LocalDate.now().format(DAY_FORMAT);
        String storedName = IdUtil.fastSimpleUUID() + (StrUtil.isBlank(extension) ? "" : "." + extension);
        String relativeDir = StrUtil.blankToDefault(sanitize(bizType), "OTHER") + File.separator + day;
        // Servlet Part.write interprets relative targets against Tomcat's temporary work directory.
        // Resolve under the configured business directory before mkdir and transferTo.
        File dir = new File(fileProperties.getUploadPath(), relativeDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException(ResultCode.FILE_ERROR, "创建文件存储目录失败");
        }
        File target = new File(dir, storedName);

        try {
            file.transferTo(target);
        } catch (IOException e) {
            log.error("文件落盘失败", e);
            throw new BizException(ResultCode.FILE_ERROR, "文件保存失败，请联系学校管理员检查存储目录");
        }

        String fileHash;
        try {
            fileHash = HashUtil.sha256File(target);
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_ERROR, "计算文件哈希失败，请稍后重试");
        }

        FileRecord record = new FileRecord();
        record.setFileNo("FILE" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase());
        record.setOriginalName(originalName);
        record.setStoredName(storedName);
        record.setFilePath(target.getAbsolutePath());
        record.setFileHash(fileHash);
        record.setFileSize(file.getSize());
        record.setContentType(file.getContentType());
        record.setBizType(StrUtil.blankToDefault(bizType, "OTHER"));
        record.setBizId(bizId);
        record.setUploaderId(SecurityUtil.getUserIdOrNull());
        record.setOnChain(0);
        record.setHashVerified(0);
        record.setDeleted(0);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        fileRecordMapper.insert(record);

        record.setFileUrl(fileProperties.getUrlPrefix() + record.getFileId());
        fileRecordMapper.updateById(record);

        log.info("文件上传成功 fileId={} size={}", record.getFileId(),file.getSize());
        return toVO(record);
    }

    @Override
    public void download(Long fileId, HttpServletResponse response) {
        FileRecord record = getById(fileId);
        assertDownloadAllowed(record);
        File file = new File(record.getFilePath());
        if (!file.exists()) {
            throw new BizException(ResultCode.FILE_ERROR, "原始文件不存在（可能已被清理）");
        }
        try {
            response.setContentType(StrUtil.blankToDefault(record.getContentType(), "application/octet-stream"));
            response.setHeader("Content-Disposition", "attachment; filename="
                    + URLEncoder.encode(record.getOriginalName(), "UTF-8"));
            response.setContentLengthLong(file.length());
            try (OutputStream out = response.getOutputStream()) {
                Files.copy(file.toPath(), out);
            }
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_ERROR, "文件下载失败：" + e.getMessage());
        }
    }

    /**
     * 原始文件属于校内业务数据，不能仅依赖文件 ID 作为安全边界。
     * 管理员/审计员可履行监管职责；社团只能读取自己项目；学生只能读取公开项目的非敏感材料。
     */
    private void assertDownloadAllowed(FileRecord record) {
        com.campus.charity.security.LoginUser user = SecurityUtil.getLoginUser();
        if (SecurityUtil.isAdmin() || SecurityUtil.isAuditor()
                || user.getUserId().equals(record.getUploaderId())) {
            return;
        }
        if("REFUND_VOUCHER".equals(record.getBizType()) && user.getUserId().equals(fileRecordMapper.findRefundApplicant(record.getFileId()))) return;

        Long projectId = fileRecordMapper.findProjectId(record.getFileId());
        if (projectId == null) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该原始文件");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ResultCode.FORBIDDEN, "关联项目不存在或已删除");
        }
        if (SecurityUtil.isClub()) {
            if (project.getClubId() != null && project.getClubId().equals(user.getUserId())) {
                return;
            }
            throw new BizException(ResultCode.FORBIDDEN, "只能访问本社团项目文件");
        }
        if (SecurityUtil.isStudent()) {
            String materialType = fileRecordMapper.findMaterialType(record.getFileId());
            boolean sensitive = "BENEFICIARY".equalsIgnoreCase(materialType)
                    || "QUALIFICATION".equalsIgnoreCase(record.getBizType())
                    || "AUDIT_REPORT".equalsIgnoreCase(record.getBizType());
            boolean publicProject = ProjectStatusConst.ONLINE.equals(project.getStatus()) || ProjectStatusConst.FINISH_PENDING.equals(project.getStatus())
                    || ProjectStatusConst.FINISHED.equals(project.getStatus())
                    || ProjectStatusConst.SETTLED.equals(project.getStatus());
            if (publicProject && !sensitive) {
                return;
            }
        }
        throw new BizException(ResultCode.FORBIDDEN, "无权访问该原始文件");
    }

    @Override
    public FileRecord getById(Long fileId) {
        FileRecord record = fileRecordMapper.selectById(fileId);
        if (record == null) {
            throw new BizException(ResultCode.FILE_ERROR, "文件记录不存在");
        }
        return record;
    }

    @Override
    public void markOnChain(Long fileId, String txId, Long blockHeight) {
        if (fileId == null || txId == null) {
            return;
        }
        FileRecord record = fileRecordMapper.selectById(fileId);
        if (record == null) {
            return;
        }
        record.setOnChain(1);
        record.setTxId(txId);
        record.setBlockHeight(blockHeight);
        record.setUpdateTime(LocalDateTime.now());
        fileRecordMapper.updateById(record);
    }

    @Override
    public FileDtos.FileVerifyVO verify(Long fileId, MultipartFile file) {
        FileRecord record = getById(fileId);
        assertDownloadAllowed(record);
        String uploadedHash;
        try {
            uploadedHash = HashUtil.sha256Hex(file.getBytes());
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_ERROR, "计算上传文件哈希失败");
        }
        FileDtos.FileVerifyVO vo = new FileDtos.FileVerifyVO();
        vo.setFileId(String.valueOf(record.getFileId()));
        vo.setFileName(record.getOriginalName());
        vo.setStoredHash(record.getFileHash());
        vo.setUploadedHash(uploadedHash);
        vo.setMatched(record.getFileHash().equalsIgnoreCase(uploadedHash));
        vo.setMessage(vo.isMatched() ? "哈希一致，文件未被篡改" : "哈希不一致，文件可能已被篡改");
        record.setHashVerified(vo.isMatched() ? 1 : 2);
        record.setVerifyTime(LocalDateTime.now());
        fileRecordMapper.updateById(record);
        return vo;
    }

    @Override
    public FileDtos.FileVerifyVO verifyByStoredFile(Long fileId) {
        FileRecord record = getById(fileId);
        assertDownloadAllowed(record);
        String currentHash;
        try {
            currentHash = HashUtil.sha256File(new File(record.getFilePath()));
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_ERROR, "读取服务器原始文件失败");
        }
        FileDtos.FileVerifyVO vo = new FileDtos.FileVerifyVO();
        vo.setFileId(String.valueOf(record.getFileId()));
        vo.setFileName(record.getOriginalName());
        vo.setStoredHash(record.getFileHash());
        vo.setUploadedHash(currentHash);
        vo.setMatched(record.getFileHash().equalsIgnoreCase(currentHash));
        vo.setMessage(vo.isMatched() ? "服务器原始文件与登记哈希一致" : "服务器原始文件已被篡改");
        record.setHashVerified(vo.isMatched() ? 1 : 2);
        record.setVerifyTime(LocalDateTime.now());
        fileRecordMapper.updateById(record);
        return vo;
    }

    private FileDtos.FileVO toVO(FileRecord record) {
        FileDtos.FileVO vo = new FileDtos.FileVO();
        vo.setFileId(record.getFileId());
        vo.setFileNo(record.getFileNo());
        vo.setFileName(record.getOriginalName());
        vo.setFileUrl(record.getFileUrl());
        vo.setFileHash(record.getFileHash());
        vo.setFileSize(record.getFileSize());
        vo.setContentType(record.getContentType());
        vo.setBizType(record.getBizType());
        vo.setBizId(record.getBizId());
        vo.setOnChain(record.getOnChain());
        vo.setTxId(record.getTxId());
        vo.setBlockHeight(record.getBlockHeight());
        vo.setUploadTime(record.getCreateTime());
        return vo;
    }

    private String sanitize(String value) {
        if (StrUtil.isBlank(value)) {
            return "OTHER";
        }
        return value.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
