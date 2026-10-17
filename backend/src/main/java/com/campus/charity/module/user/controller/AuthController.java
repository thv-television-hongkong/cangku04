package com.campus.charity.module.user.controller;

import com.campus.charity.common.api.R;
import com.campus.charity.module.user.dto.UserDtos;
import com.campus.charity.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 认证接口：注册登录（include 找回密码）。
 */
@Tag(name = "01-认证与用户", description = "注册 / 登录 / 找回密码")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "发送验证码（注册 / 找回密码）")
    @PostMapping("/send-code")
    public R<String> sendCode(@RequestBody @Validated UserDtos.SendCodeDTO dto) {
        String code = userService.sendVerifyCode(dto);
        return R.ok("验证码已发送（校内演示环境直接返回）", code);
    }

    @Operation(summary = "注册（普通学生 / 公益社团，社团需资质审核）")
    @PostMapping("/register")
    public R<UserDtos.LoginVO> register(@RequestBody @Valid UserDtos.RegisterDTO dto) {
        return R.ok("注册成功", userService.register(dto));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<UserDtos.LoginVO> login(@RequestBody @Valid UserDtos.LoginDTO dto) {
        return R.ok("登录成功", userService.login(dto));
    }

    @Operation(summary = "找回密码（验证码重置）")
    @PostMapping("/reset-password")
    public R<Void> resetPassword(@RequestBody @Valid UserDtos.ResetPasswordDTO dto) {
        userService.resetPassword(dto);
        return R.ok("密码重置成功", null);
    }
}
