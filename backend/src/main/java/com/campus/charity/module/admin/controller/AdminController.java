package com.campus.charity.module.admin.controller;

import com.campus.charity.common.annotation.OpLog;
import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.R;
import com.campus.charity.module.admin.entity.Complaint;
import com.campus.charity.module.admin.entity.Publicity;
import com.campus.charity.module.admin.entity.SysBackupRecord;
import com.campus.charity.module.admin.entity.SysOperationLog;
import com.campus.charity.module.admin.service.AdminService;
import com.campus.charity.module.evidence.entity.ContractRecord;
import com.campus.charity.module.user.dto.UserDtos;
import com.campus.charity.module.user.entity.ClubQualification;
import com.campus.charity.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;
import java.util.Map;

/**
 * 平台管理员后台接口（用户管理 / 资质审核 / 数据统计 / 日志 / 公示 / 投诉 / 备份 / 合约）。
 */
@Tag(name = "04-管理员后台", description = "用户与权限 / 社团资质 / 统计 / 日志 / 公示 / 投诉 / 备份 / 合约")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;

    public AdminController(AdminService adminService, UserService userService) {
        this.adminService = adminService;
        this.userService = userService;
    }

    // ------------------------------ 数据统计 ------------------------------

    @Operation(summary = "运营数据总览（含统计图表数据）")
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(adminService.dashboard());
    }

    @Operation(summary = "数据统计与报表（include 生成统计图表）")
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics() {
        return R.ok(adminService.statistics());
    }

    // ------------------------------ 用户管理 ------------------------------

    @Operation(summary = "用户列表（include 用户权限分配）")
    @GetMapping("/users")
    public R<PageResult<UserDtos.UserVO>> users(UserDtos.UserQueryDTO query) {
        return R.ok(userService.pageUsers(query));
    }

    @Operation(summary = "新增管理员 / 监管审计账号")
    @PostMapping("/users")
    @OpLog(module = "用户管理", value = "新增系统账号")
    public R<Long> createUser(@RequestBody UserDtos.CreateSystemUserDTO dto) {
        return R.ok("账号创建成功", userService.createSystemUser(dto));
    }

    @Operation(summary = "冻结 / 解冻账号")
    @PutMapping("/users/{userId}/status")
    @OpLog(module = "用户管理", value = "冻结或解冻账号")
    public R<Void> updateStatus(@PathVariable Long userId,
                                @RequestParam Integer status,
                                @RequestParam(required = false) String remark) {
        userService.updateUserStatus(userId, status, remark);
        return R.ok("账号状态已更新", null);
    }

    @Operation(summary = "权限分配（角色变更）")
    @PutMapping("/users/{userId}/role")
    @OpLog(module = "用户管理", value = "分配用户角色权限")
    public R<Void> updateRole(@PathVariable Long userId, @RequestParam String role) {
        userService.updateUserRole(userId, role);
        return R.ok("权限已更新", null);
    }

    @Operation(summary = "重置用户密码")
    @PutMapping("/users/{userId}/reset-password")
    @OpLog(module = "用户管理", value = "重置用户密码")
    public R<Void> resetPassword(@PathVariable Long userId) {
        userService.resetPasswordByAdmin(userId);
        return R.ok("密码已重置为默认密码", null);
    }

    // ------------------------------ 社团资质 ------------------------------

    @Operation(summary = "社团资质审核列表")
    @GetMapping("/qualifications")
    public R<PageResult<ClubQualification>> qualifications(@RequestParam(required = false) Integer status,
                                                           @RequestParam(defaultValue = "1") long current,
                                                           @RequestParam(defaultValue = "10") long size) {
        return R.ok(userService.pageQualifications(status, current, size));
    }

    @Operation(summary = "审核社团资质")
    @PostMapping("/qualifications/audit")
    @OpLog(module = "用户管理", value = "审核社团资质")
    public R<Void> auditQualification(@RequestBody UserDtos.QualificationAuditDTO dto) {
        userService.auditQualification(dto);
        return R.ok("资质审核完成", null);
    }

    // ------------------------------ 系统日志 ------------------------------

    @Operation(summary = "系统日志（include 操作审计追踪）")
    @GetMapping("/logs")
    public R<PageResult<SysOperationLog>> logs(@RequestParam(required = false) String module,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size) {
        return R.ok(adminService.pageLogs(module, keyword, current, size));
    }

    @Operation(summary = "操作审计行为统计")
    @GetMapping("/logs/statistics")
    public R<List<Map<String, Object>>> logStatistics() {
        return R.ok(adminService.logStatistics());
    }

    // ------------------------------ 公益公示 ------------------------------

    @Operation(summary = "公益信息公示列表")
    @GetMapping("/publicity")
    public R<PageResult<Publicity>> publicity(@RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size) {
        return R.ok(adminService.pagePublicity(status, current, size));
    }

    @Operation(summary = "发布校内公益公示")
    @PostMapping("/publicity")
    @OpLog(module = "后台监管", value = "发布公益项目公示")
    public R<Long> publish(@RequestBody Publicity publicity) {
        return R.ok("公示已发布", adminService.publish(publicity));
    }

    @Operation(summary = "撤回公示")
    @PostMapping("/publicity/{publicityId}/revoke")
    @OpLog(module = "后台监管", value = "撤回公益公示")
    public R<Void> revoke(@PathVariable Long publicityId) {
        adminService.revokePublicity(publicityId);
        return R.ok("公示已撤回", null);
    }

    // ------------------------------ 投诉与纠纷 ------------------------------

    @Operation(summary = "投诉与纠纷列表")
    @GetMapping("/complaints")
    public R<PageResult<Complaint>> complaints(@RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size) {
        return R.ok(adminService.pageComplaints(status, projectId, current, size));
    }

    @Operation(summary = "处理投诉与纠纷（extends 通知相关方）")
    @PostMapping("/complaints/{complaintId}/handle")
    @OpLog(module = "后台监管", value = "处理投诉纠纷并通知相关方")
    public R<Void> handleComplaint(@PathVariable Long complaintId,
                                   @RequestParam Integer status,
                                   @RequestParam String result) {
        adminService.handleComplaint(complaintId, status, result);
        return R.ok("处理完成并已通知相关方", null);
    }

    // ------------------------------ 数据库备份 ------------------------------

    @Operation(summary = "数据库备份记录")
    @GetMapping("/backups")
    public R<PageResult<SysBackupRecord>> backups(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size) {
        return R.ok(adminService.pageBackups(current, size));
    }

    @Operation(summary = "手动执行数据库备份")
    @PostMapping("/backups/run")
    @OpLog(module = "系统维护", value = "手动执行数据库备份", saveParams = false)
    public R<SysBackupRecord> runBackup() {
        return R.ok("备份任务已执行", adminService.runBackup("MANUAL"));
    }

    // ------------------------------ 智能合约 ------------------------------

    @Operation(summary = "智能合约部署 / 升级记录")
    @GetMapping("/contracts")
    public R<List<ContractRecord>> contracts() {
        return R.ok(adminService.listContracts());
    }

    @Operation(summary = "记录智能合约部署 / 升级（extends 部署合约升级）")
    @PostMapping("/contracts")
    @OpLog(module = "智能合约", value = "记录合约部署或升级")
    public R<ContractRecord> recordContract(@RequestBody ContractRecord record) {
        return R.ok("维护登记已保存，存证已排队；登记不等于完成 Fabric 部署", adminService.recordContract(record));
    }

    @Operation(summary = "区块链节点与合约运行状态")
    @GetMapping("/chain/status")
    public R<Map<String, Object>> chainStatus() {
        return R.ok(adminService.chainStatus());
    }
}
