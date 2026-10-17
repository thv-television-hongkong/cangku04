package com.campus.charity.module.audit.controller;

import com.campus.charity.common.annotation.OpLog;
import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.R;
import com.campus.charity.module.audit.entity.AuditReport;
import com.campus.charity.module.audit.entity.AuditRule;
import com.campus.charity.module.audit.entity.WarningRecord;
import com.campus.charity.module.audit.service.AuditReportService;
import com.campus.charity.module.audit.service.WarningService;
import com.campus.charity.module.project.dto.ProjectDtos;
import com.campus.charity.module.project.service.ProjectService;
import com.campus.charity.module.trace.dto.TraceDtos;
import com.campus.charity.module.trace.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 监管审计方接口：项目真实性核验、存证哈希验证、资金链追踪、审计报告、预警规则。
 */
@Tag(name = "09-监管审计", description = "项目核验 / 存证验真 / 资金追踪 / 审计报告 / 预警规则")
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('AUDITOR','ADMIN')")
public class AuditController {

    private final ProjectService projectService;
    private final TraceService traceService;
    private final WarningService warningService;
    private final AuditReportService reportService;

    public AuditController(ProjectService projectService,
                           TraceService traceService,
                           WarningService warningService,
                           AuditReportService reportService) {
        this.projectService = projectService;
        this.traceService = traceService;
        this.warningService = warningService;
        this.reportService = reportService;
    }

    // ------------------------------ 项目真实性核验 ------------------------------

    @Operation(summary = "查看全部众筹项目（include 项目真实性核验）")
    @GetMapping("/projects")
    public R<PageResult<ProjectDtos.ProjectVO>> projects(ProjectDtos.ProjectQuery query) {
        query.setSize(query.getSize() <= 0 ? 10 : query.getSize());
        return R.ok(projectService.pageAll(query));
    }

    @Operation(summary = "项目详情核验（含审核记录、存证、资金）")
    @GetMapping("/projects/{projectId}")
    public R<ProjectDtos.ProjectDetailVO> projectDetail(@PathVariable Long projectId) {
        return R.ok(projectService.detail(projectId));
    }

    // ------------------------------ 存证核验 / 资金链追踪 ------------------------------

    @Operation(summary = "查看区块链存证（include 项目全流程）")
    @PostMapping("/evidence/query")
    public R<TraceDtos.TraceResult> evidenceQuery(@RequestBody TraceDtos.TraceQuery query) {
        return R.ok(traceService.query(query));
    }

    @Operation(summary = "存证哈希验证（extends 存证哈希验证）")
    @PostMapping("/evidence/verify")
    public R<TraceDtos.VerifyResult> evidenceVerify(@RequestBody TraceDtos.VerifyForm form) {
        return R.ok(traceService.verify(form));
    }

    @Operation(summary = "完整追踪资金全链路（include 资金链追踪）")
    @GetMapping("/fund-trace/{projectId}")
    public R<TraceDtos.TraceResult> fundTrace(@PathVariable Long projectId) {
        return R.ok(traceService.projectTrace(projectId));
    }

    @Operation(summary = "区块链存证总览")
    @GetMapping("/chain/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(traceService.chainOverview());
    }

    // ------------------------------ 预警规则 ------------------------------

    @Operation(summary = "审核 / 预警规则列表")
    @GetMapping("/rules")
    public R<List<AuditRule>> rules(@RequestParam(required = false) Boolean enabled) {
        return R.ok(warningService.listRules(enabled));
    }

    @Operation(summary = "新增预警规则（extends 设置预警规则）")
    @PostMapping("/rules")
    @OpLog(module = "监管审计", value = "新增异常交易预警规则")
    public R<AuditRule> saveRule(@RequestBody AuditRule rule) {
        return R.ok("预警规则已新增", warningService.saveRule(rule));
    }

    @Operation(summary = "修改预警规则")
    @PutMapping("/rules")
    @OpLog(module = "监管审计", value = "修改异常交易预警规则")
    public R<Void> updateRule(@RequestBody AuditRule rule) {
        warningService.updateRule(rule);
        return R.ok("预警规则已更新", null);
    }

    @Operation(summary = "启用 / 停用预警规则")
    @PostMapping("/rules/{ruleId}/toggle")
    @OpLog(module = "监管审计", value = "启用或停用预警规则")
    public R<Void> toggleRule(@PathVariable Long ruleId, @RequestParam boolean enabled) {
        warningService.toggleRule(ruleId, enabled);
        return R.ok("规则状态已更新", null);
    }

    @Operation(summary = "删除预警规则")
    @DeleteMapping("/rules/{ruleId}")
    @OpLog(module = "监管审计", value = "删除预警规则")
    public R<Void> deleteRule(@PathVariable Long ruleId) {
        warningService.deleteRule(ruleId);
        return R.ok("规则已删除", null);
    }

    // ------------------------------ 异常预警 ------------------------------

    @Operation(summary = "异常交易预警列表")
    @GetMapping("/warnings")
    public R<PageResult<WarningRecord>> warnings(@RequestParam(required = false) Integer handleStatus,
                                                 @RequestParam(required = false) String level,
                                                 @RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "10") long size) {
        return R.ok(warningService.pageWarnings(handleStatus, level, current, size));
    }

    @Operation(summary = "处理预警")
    @PostMapping("/warnings/{warningId}/handle")
    @OpLog(module = "监管审计", value = "处理异常交易预警")
    public R<Void> handleWarning(@PathVariable Long warningId,
                                 @RequestParam Integer handleStatus,
                                 @RequestParam(required = false) String opinion) {
        warningService.handleWarning(warningId, handleStatus, opinion);
        return R.ok("预警已处理", null);
    }

    @Operation(summary = "预警级别统计")
    @GetMapping("/warnings/statistics")
    public R<List<Map<String, Object>>> warningStatistics() {
        return R.ok(warningService.countByLevel());
    }

    // ------------------------------ 审计报告 ------------------------------

    @Operation(summary = "生成审计报告（include 导出存证数据）")
    @PostMapping("/reports")
    @OpLog(module = "监管审计", value = "生成审计报告并上链存证")
    public R<AuditReport> generateReport(@RequestParam(defaultValue = "COMPREHENSIVE") String reportType,
                                         @RequestParam(required = false) Long projectId,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return R.ok("审计报告已生成，摘要存证已进入队列",
                reportService.generate(reportType, projectId, startTime, endTime));
    }

    @Operation(summary = "审计报告列表")
    @GetMapping("/reports")
    public R<PageResult<AuditReport>> reports(@RequestParam(required = false) Long projectId,
                                              @RequestParam(required = false) String reportType,
                                              @RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size) {
        return R.ok(reportService.page(projectId, reportType, current, size));
    }

    @Operation(summary = "审计报告详情")
    @GetMapping("/reports/{reportId}")
    public R<AuditReport> reportDetail(@PathVariable Long reportId) {
        return R.ok(reportService.getById(reportId));
    }

    @Operation(summary = "导出审计报告明细数据（存证数据导出行）")
    @GetMapping("/reports/{reportId}/export")
    public R<List<Map<String, Object>>> exportReport(@PathVariable Long reportId) {
        return R.ok(reportService.exportRows(reportId));
    }
}
