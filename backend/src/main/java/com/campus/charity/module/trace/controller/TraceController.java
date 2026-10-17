package com.campus.charity.module.trace.controller;

import com.campus.charity.common.api.R;
import com.campus.charity.module.trace.dto.TraceDtos;
import com.campus.charity.module.trace.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 溯源查询接口：项目 ID / 交易 ID / 区块高度多维度查询、哈希校验、全流程溯源。
 */
@Tag(name = "08-溯源查询", description = "多维存证查询 / 哈希校验 / 全流程溯源")
@RestController
@RequestMapping("/api/trace")
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    @Operation(summary = "多维溯源查询（项目ID / 交易ID / 区块高度 / 业务ID）")
    @PostMapping("/query")
    public R<TraceDtos.TraceResult> query(@RequestBody TraceDtos.TraceQuery query) {
        return R.ok(traceService.query(query));
    }

    @Operation(summary = "哈希验真（提供业务ID与哈希 / 或文件ID）")
    @PostMapping("/verify")
    public R<TraceDtos.VerifyResult> verify(@RequestBody TraceDtos.VerifyForm form) {
        return R.ok(traceService.verify(form));
    }

    @Operation(summary = "仅依据链上数据验真（业务库损坏时仍可核验）")
    @PostMapping("/verify-by-chain")
    public R<TraceDtos.VerifyResult> verifyByChain(@RequestBody TraceDtos.VerifyForm form) {
        return R.ok(traceService.verifyByChainOnly(form));
    }

    @Operation(summary = "哈希校验工具：直接输入业务ID与哈希")
    @GetMapping("/verify/{bizType}/{bizId}")
    public R<TraceDtos.VerifyResult> verifyGet(@PathVariable String bizType,
                                               @PathVariable String bizId,
                                               @org.springframework.web.bind.annotation.RequestParam String hash) {
        TraceDtos.VerifyForm form = new TraceDtos.VerifyForm();
        form.setBizType(bizType);
        form.setBizId(bizId);
        form.setHash(hash);
        return R.ok(traceService.verify(form));
    }

    @Operation(summary = "项目全流程溯源（项目 -> 审核 -> 捐赠 -> 提取 -> 资金凭证 -> 帮扶材料）")
    @GetMapping("/project/{projectId}")
    public R<TraceDtos.TraceResult> projectTrace(@PathVariable Long projectId) {
        return R.ok(traceService.projectTrace(projectId));
    }

    @Operation(summary = "文件哈希核验（原始文件 vs 链上存证）")
    @GetMapping("/file/{fileId}")
    public R<TraceDtos.VerifyResult> verifyFile(@PathVariable Long fileId) {
        return R.ok(traceService.verifyFile(fileId));
    }

    @Operation(summary = "区块链存证总览（链码版本 / 节点 / 存证数量 / 链式完整性）")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(traceService.chainOverview());
    }

    @Operation(summary = "导出存证数据（监管审计：include 导出存证数据）")
    @PostMapping("/export")
    public R<List<Map<String, Object>>> export(@RequestBody(required = false) TraceDtos.TraceQuery query) {
        return R.ok(traceService.exportEvidence(query));
    }
}
