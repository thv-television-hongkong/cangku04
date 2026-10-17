package com.campus.charity.module.fund.controller;

import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.R;
import com.campus.charity.module.fund.dto.FundDtos;
import com.campus.charity.module.fund.entity.FundWithdrawal;
import com.campus.charity.module.fund.service.FundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 资金接口：资金使用凭证上传（ER 实体 5）、资金提取申请、资金全景查询。
 */
@Tag(name = "07-资金管理", description = "资金使用凭证 / 资金提取 / 资金流向")
@RestController
@RequestMapping("/api/fund")
public class FundController {

    private final FundService fundService;

    public FundController(FundService fundService) {
        this.fundService = fundService;
    }

    @Operation(summary = "上传资金使用凭证（extends 查看存证溯源，文件哈希上链）")
    @PostMapping("/usages")
    @PreAuthorize("hasAnyRole('CLUB','ADMIN')")
    public R<Long> uploadUsage(@RequestBody FundDtos.UsageForm form) {
        return R.ok("资金使用凭证已保存，摘要存证已进入队列", fundService.uploadUsage(form));
    }

    @Operation(summary = "资金使用凭证分页查询")
    @GetMapping("/usages")
    public R<PageResult<Map<String, Object>>> usages(@RequestParam(required = false) Long projectId,
                                                     @RequestParam(required = false) Integer auditStatus,
                                                     @RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "10") long size) {
        return R.ok(fundService.pageUsages(projectId, auditStatus, current, size));
    }

    @Operation(summary = "审核资金使用凭证（管理员）")
    @PostMapping("/usages/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> auditUsage(@RequestBody FundDtos.UsageAuditForm form) {
        fundService.auditUsage(form);
        return R.ok("审核完成", null);
    }

    @Operation(summary = "申请资金提取（extends 提交区块链存证）")
    @PostMapping("/withdrawals")
    @PreAuthorize("hasAnyRole('CLUB','ADMIN')")
    public R<Long> applyWithdrawal(@RequestBody FundDtos.WithdrawalForm form) {
        return R.ok("模拟提取申请已保存，摘要存证已进入队列", fundService.applyWithdrawal(form));
    }

    @Operation(summary = "资金提取申请分页查询")
    @GetMapping("/withdrawals")
    public R<PageResult<FundWithdrawal>> withdrawals(@RequestParam(required = false) Long projectId,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "10") long size) {
        return R.ok(fundService.pageWithdrawals(projectId, status, current, size));
    }

    @Operation(summary = "审核 / 发放资金提取（管理员）")
    @PostMapping("/withdrawals/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> auditWithdrawal(@RequestBody FundDtos.WithdrawalAuditForm form) {
        fundService.auditWithdrawal(form);
        return R.ok("处理完成", null);
    }

    @Operation(summary = "项目资金全景（筹款 / 提取 / 使用 / 结余 / 明细，可追溯）")
    @GetMapping("/overview/{projectId}")
    public R<Map<String, Object>> overview(@PathVariable Long projectId) {
        return R.ok(fundService.fundOverview(projectId));
    }
}
