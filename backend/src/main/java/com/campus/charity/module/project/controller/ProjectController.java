package com.campus.charity.module.project.controller;

import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.R;
import com.campus.charity.module.project.dto.ProjectDtos;
import com.campus.charity.module.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 捐赠者用例：浏览众筹项目列表、查看项目详情、收藏、分享、评价、查看资金流向。
 */
@Tag(name = "02-众筹项目", description = "项目浏览 / 详情 / 收藏 / 分享 / 评价 / 资金流向")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "浏览众筹项目列表（include 查看区块链存证）")
    @GetMapping
    public R<PageResult<ProjectDtos.ProjectVO>> list(ProjectDtos.ProjectQuery query) {
        return R.ok(projectService.pageOnline(query));
    }

    @Operation(summary = "查看项目详情（include 收藏项目）")
    @GetMapping("/{projectId}")
    public R<ProjectDtos.ProjectDetailVO> detail(@PathVariable Long projectId) {
        return R.ok(projectService.detail(projectId));
    }

    @Operation(summary = "收藏 / 取消收藏项目")
    @PostMapping("/{projectId}/favorite")
    public R<Boolean> favorite(@PathVariable Long projectId) {
        boolean favorited = projectService.toggleFavorite(projectId);
        return R.ok(favorited ? "收藏成功" : "已取消收藏", favorited);
    }

    @Operation(summary = "我的收藏列表")
    @GetMapping("/favorites/mine")
    public R<PageResult<ProjectDtos.ProjectVO>> myFavorites(ProjectDtos.ProjectQuery query) {
        return R.ok(projectService.pageMyFavorites(query));
    }

    @Operation(summary = "分享项目（extends：分享项目）")
    @PostMapping("/{projectId}/share")
    public R<Void> share(@PathVariable Long projectId,
                         @RequestParam(value = "channel", required = false) String channel) {
        projectService.share(projectId, channel);
        return R.ok("分享成功", null);
    }

    @Operation(summary = "评价项目（捐赠完成后可评价）")
    @PostMapping("/reviews")
    public R<Long> review(@RequestBody ProjectDtos.ReviewForm form) {
        return R.ok("评价成功", projectService.review(form));
    }

    @Operation(summary = "项目评价列表")
    @GetMapping("/{projectId}/reviews")
    public R<PageResult<Map<String, Object>>> reviews(@PathVariable Long projectId,
                                                      @RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "10") long size) {
        return R.ok(projectService.pageReviews(projectId, current, size));
    }

    @Operation(summary = "查看资金流向（extends 分享项目）")
    @GetMapping("/{projectId}/fund-flow")
    public R<Map<String, Object>> fundFlow(@PathVariable Long projectId) {
        return R.ok(projectService.fundFlow(projectId));
    }

    @Operation(summary = "项目进度列表（含链上存证信息）")
    @GetMapping("/{projectId}/progress")
    public R<List<Map<String, Object>>> progress(@PathVariable Long projectId) {
        return R.ok(projectService.listProgress(projectId));
    }

    @Operation(summary = "项目材料列表（帮扶材料 / 项目计划书，均含文件哈希）")
    @GetMapping("/{projectId}/materials")
    public R<List<Map<String, Object>>> materials(@PathVariable Long projectId) {
        return R.ok(projectService.listMaterials(projectId));
    }

    @Operation(summary = "项目审核 / 状态变更记录（溯源监督）")
    @GetMapping("/{projectId}/audits")
    public R<List<Map<String, Object>>> audits(@PathVariable Long projectId) {
        return R.ok(projectService.auditHistory(projectId));
    }

    @Operation(summary = "项目捐赠人排行（脱敏展示）")
    @GetMapping("/{projectId}/donors")
    public R<List<Map<String, Object>>> donors(@PathVariable Long projectId,
                                               @RequestParam(defaultValue = "10") int limit) {
        return R.ok(projectService.donorList(projectId, limit));
    }
}
