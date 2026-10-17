package com.campus.charity.module.admin.controller;

import com.campus.charity.common.annotation.OpLog;
import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.R;
import com.campus.charity.module.admin.entity.Complaint;
import com.campus.charity.module.admin.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 投诉与纠纷（用户提交入口）。
 */
@Tag(name = "11-投诉建议", description = "用户提交投诉与查看处理进度")
@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final AdminService adminService;

    public ComplaintController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "提交投诉 / 纠纷")
    @PostMapping
    @OpLog(module = "投诉纠纷", value = "提交投诉")
    public R<Long> submit(@RequestBody Complaint complaint) {
        return R.ok("投诉已提交，管理员将尽快处理", adminService.submitComplaint(complaint));
    }

    @Operation(summary = "我的投诉列表")
    @GetMapping("/mine")
    public R<PageResult<Complaint>> mine(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size) {
        return R.ok(adminService.pageMyComplaints(current, size));
    }
}
