package com.campus.charity.module.file.controller;

import com.campus.charity.common.api.R;
import com.campus.charity.module.file.dto.FileDtos;
import com.campus.charity.module.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

/**
 * 文件接口：原始文件上传 / 下载 / 哈希比对。
 *
 * <p>上传后返回 SHA-256 哈希，业务层再用该哈希调用链码上链存证，原始文件始终保留在业务服务器。</p>
 */
@Tag(name = "06-文件与存证", description = "原始文件上传下载 / 哈希比对")
@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "上传原始文件（仅哈希上链，原始文件存业务服务器）")
    @PostMapping("/upload")
    public R<FileDtos.FileVO> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "bizType", required = false) String bizType,
                                     @RequestParam(value = "bizId", required = false) String bizId) {
        return R.ok("上传成功", fileService.upload(file, bizType, bizId));
    }

    @Operation(summary = "下载原始文件")
    @GetMapping("/download/{fileId}")
    public void download(@PathVariable Long fileId, HttpServletResponse response) {
        fileService.download(fileId, response);
    }

    @Operation(summary = "上传文件并与登记哈希比对（哈希校验工具）")
    @PostMapping("/verify/{fileId}")
    public R<FileDtos.FileVerifyVO> verify(@PathVariable Long fileId, @RequestParam("file") MultipartFile file) {
        return R.ok(fileService.verify(fileId, file));
    }

    @Operation(summary = "校验业务服务器上的原始文件是否被篡改")
    @GetMapping("/verify-stored/{fileId}")
    public R<FileDtos.FileVerifyVO> verifyStored(@PathVariable Long fileId) {
        return R.ok(fileService.verifyByStoredFile(fileId));
    }
}
