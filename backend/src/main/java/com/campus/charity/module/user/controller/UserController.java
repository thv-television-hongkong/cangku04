package com.campus.charity.module.user.controller;

import com.campus.charity.common.api.R;
import com.campus.charity.module.notice.entity.Notification;
import com.campus.charity.module.notice.service.NotificationService;
import com.campus.charity.module.user.dto.UserDtos;
import com.campus.charity.module.user.entity.ClubQualification;
import com.campus.charity.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人信息维护 / 社团资质提交 / 站内通知。
 */
@Tag(name = "01-认证与用户", description = "个人信息维护 / 社团资质 / 通知")
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final NotificationService notificationService;

    public UserController(UserService userService, NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/profile")
    public R<UserDtos.UserVO> profile() {
        return R.ok(userService.currentUser());
    }

    @Operation(summary = "个人信息维护")
    @PutMapping("/profile")
    public R<UserDtos.UserVO> updateProfile(@RequestBody UserDtos.UpdateProfileDTO dto) {
        return R.ok("个人信息已更新", userService.updateProfile(dto));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody UserDtos.ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return R.ok("密码修改成功", null);
    }

    @Operation(summary = "社团提交 / 重新提交资质审核")
    @PostMapping("/qualification")
    public R<Long> submitQualification(@RequestBody UserDtos.QualificationDTO dto) {
        return R.ok("资质材料已提交，等待管理员审核", userService.submitQualification(dto));
    }

    @Operation(summary = "查询本社团资质审核状态与审核意见")
    @GetMapping("/qualification")
    public R<ClubQualification> currentQualification() {
        return R.ok(userService.currentQualification(com.campus.charity.common.util.SecurityUtil.getUserId()));
    }

    @Operation(summary = "我的通知列表")
    @GetMapping("/notifications")
    public R<List<Notification>> notifications() {
        return R.ok(notificationService.listCurrentUser());
    }

    @Operation(summary = "未读通知数量")
    @GetMapping("/notifications/unread")
    public R<Long> unreadCount() {
        return R.ok(notificationService.unreadCount());
    }

    @Operation(summary = "标记通知已读")
    @PutMapping("/notifications/read")
    public R<Void> readNotifications(@RequestBody List<Long> noticeIds) {
        notificationService.markRead(noticeIds);
        return R.ok("已标记为已读", null);
    }
}
