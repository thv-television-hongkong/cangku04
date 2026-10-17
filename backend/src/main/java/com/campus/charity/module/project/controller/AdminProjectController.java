package com.campus.charity.module.project.controller;

import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.R;
import com.campus.charity.module.project.dto.ProjectDtos;
import com.campus.charity.module.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 平台管理员用例：审核众筹项目、管理项目信息、下架违规项目。
 */
@Tag(name = "04-管理员后台", description = "项目审核 / 项目管理 / 下架违规项目")
@RestController
@RequestMapping("/api/admin/projects")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProjectController {

    private final ProjectService projectService;

    public AdminProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "分页查询全部众筹项目（含全部状态）")
    @GetMapping
    public R<PageResult<ProjectDtos.ProjectVO>> list(ProjectDtos.ProjectQuery query) {
        return R.ok(projectService.pageAll(query));
    }

    @Operation(summary = "查看项目详情（含审核记录、存证、资金流向）")
    @GetMapping("/{projectId}")
    public R<ProjectDtos.ProjectDetailVO> detail(@PathVariable Long projectId) {
        return R.ok(projectService.detail(projectId));
    }

    @Operation(summary = "审核众筹项目（include 项目合规审查：真实性/合规性/材料完整性/预算合理性/帮扶对象真实性）")
    @PostMapping("/audit")
    public R<Void> audit(@RequestBody ProjectDtos.ProjectAuditForm form) {
        projectService.audit(form);
        return R.ok("审核完成", null);
    }

    @Operation(summary = "下架违规项目（extends 下架违规项目，处置记录上链存证）")
    @PostMapping("/offline")
    public R<Void> offline(@RequestBody ProjectDtos.OfflineForm form) {
        projectService.offline(form);
        return R.ok("项目已下架，处置摘要存证待后台处理", null);
    }

    @Operation(summary = "重新上架项目")
    @PostMapping("/{projectId}/online")
    public R<Void> online(@PathVariable Long projectId) {
        projectService.online(projectId);
        return R.ok("项目已重新上架", null);
    }

    @Operation(summary = "项目结项")
    @PostMapping("/{projectId}/finish")
    public R<Void> finish(@PathVariable Long projectId) {
        projectService.finish(projectId);
        return R.ok("结项操作已提交", null);
    }

    @PostMapping("/{projectId}/finish-audit")
    public R<Void> finishAudit(@PathVariable Long projectId,@RequestBody com.campus.charity.module.finance.FinanceForms.Audit form) {
        projectService.auditFinish(projectId,form.getResult(),form.getOpinion());return R.ok("结项审核完成",null);
    }

    @Operation(summary = "项目审核 / 状态变更记录")
    @GetMapping("/{projectId}/audits")
    public R<List<Map<String, Object>>> audits(@PathVariable Long projectId) {
        return R.ok(projectService.auditHistory(projectId));
    }
}
