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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 公益社团（受助者 / 项目发起方）用例接口。
 */
@Tag(name = "03-公益社团", description = "发起项目 / 编辑项目 / 进度 / 材料 / 捐赠明细 / 审核状态")
@RestController
@RequestMapping("/api/club/projects")
@PreAuthorize("hasAnyRole('CLUB','ADMIN')")
public class ClubProjectController {

    private final ProjectService projectService;

    public ClubProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "发布众筹项目（include 设置筹款目标）")
    @PostMapping
    public R<Long> create(@RequestBody ProjectDtos.ProjectForm form) {
        return R.ok("项目已提交，等待管理员审核", projectService.create(form));
    }

    @Operation(summary = "编辑众筹项目（include 更新项目进度，仅未审核项目可编辑）")
    @PutMapping
    public R<Void> update(@RequestBody ProjectDtos.ProjectForm form) {
        projectService.update(form);
        return R.ok("项目已更新", null);
    }

    @Operation(summary = "提交项目进入审核流程")
    @PostMapping("/{projectId}/submit")
    public R<Void> submit(@PathVariable Long projectId) {
        projectService.submit(projectId);
        return R.ok("已提交审核", null);
    }

    @Operation(summary = "本社团全部项目")
    @GetMapping("/mine")
    public R<PageResult<ProjectDtos.ProjectVO>> myProjects(ProjectDtos.ProjectQuery query) {
        return R.ok(projectService.pageMyProjects(query));
    }

    @Operation(summary = "查看项目审核状态（include 查看审核意见）")
    @GetMapping("/{projectId}/audits")
    public R<List<Map<String, Object>>> audits(@PathVariable Long projectId) {
        return R.ok(projectService.auditHistory(projectId));
    }

    @Operation(summary = "查看本社团项目捐赠明细（include 查看资金流向）")
    @GetMapping("/{projectId}/donations")
    public R<List<Map<String, Object>>> donations(@PathVariable Long projectId,
                                                  @RequestParam(defaultValue = "50") int limit) {
        return R.ok(projectService.donorList(projectId, limit));
    }

    @Operation(summary = "项目资金流向")
    @GetMapping("/{projectId}/fund-flow")
    public R<Map<String, Object>> fundFlow(@PathVariable Long projectId) {
        return R.ok(projectService.fundFlow(projectId));
    }

    @Operation(summary = "提交项目 / 帮扶证明材料（extends 材料哈希上链存证）")
    @PostMapping("/materials")
    public R<Long> uploadMaterial(@RequestBody ProjectDtos.MaterialForm form) {
        return R.ok("材料已提交，摘要存证待后台处理", projectService.uploadMaterial(form));
    }

    @Operation(summary = "项目材料列表")
    @GetMapping("/{projectId}/materials")
    public R<List<Map<String, Object>>> materials(@PathVariable Long projectId) {
        return R.ok(projectService.listMaterials(projectId));
    }

    @Operation(summary = "新增项目进度（include 更新项目进度）")
    @PostMapping("/progress")
    public R<Long> addProgress(@RequestBody ProjectDtos.ProgressForm form) {
        return R.ok("进度已更新，摘要存证待后台处理", projectService.addProgress(form));
    }

    @Operation(summary = "项目进度列表")
    @GetMapping("/{projectId}/progress")
    public R<List<Map<String, Object>>> progress(@PathVariable Long projectId) {
        return R.ok(projectService.listProgress(projectId));
    }

    @Operation(summary = "项目结项")
    @PostMapping("/{projectId}/finish")
    public R<Void> finish(@PathVariable Long projectId) {
        projectService.finish(projectId);
        return R.ok("结项操作已提交", null);
    }

    @Operation(summary = "回复项目评价")
    @PostMapping("/reviews/reply")
    public R<Void> replyReview(@RequestBody ProjectDtos.ReviewReplyForm form) {
        projectService.replyReview(form);
        return R.ok("回复成功", null);
    }
}
