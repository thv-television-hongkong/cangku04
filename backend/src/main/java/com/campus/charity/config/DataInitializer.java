package com.campus.charity.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.charity.common.constant.CommonConst;
import com.campus.charity.common.constant.RoleConst;
import com.campus.charity.module.user.entity.SysUser;
import com.campus.charity.module.user.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 内置账号初始化：保证系统首次启动即可登录演示。
 *
 * <p>默认账号（密码均为 123456）：admin（平台管理员）、auditor（监管审计）、
 * club001（公益社团）、20210001（普通学生）。密码使用 BCrypt 加密存储。</p>
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private static final String DEFAULT_PASSWORD = "123456";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${campus.init-default-users:true}")
    private boolean initDefaultUsers;

    public DataInitializer(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!initDefaultUsers) {
            return;
        }
        createIfAbsent("admin", "系统管理员", RoleConst.ADMIN, "ADMIN001", "信息化建设中心");
        createIfAbsent("auditor", "审计员张老师", RoleConst.AUDITOR, "AUDIT001", "审计监察处");
        createIfAbsent("club001", "阳光助学社", RoleConst.CLUB, "CLUB001", "学生工作部");
        createIfAbsent("20210001", "李同学", RoleConst.STUDENT, "2021000101", "计算机学院");
    }

    private void createIfAbsent(String account, String realName, String role, String identityNo, String college) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getAccount, account));
        if (count != null && count > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setAccount(account);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRealName(realName);
        user.setRole(role);
        user.setIdentityNo(identityNo);
        user.setCollege(college);
        user.setAuthStatus(CommonConst.AUTH_PASSED);
        user.setStatus(CommonConst.USER_NORMAL);
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        log.info("初始化账号完成 role={}",role);
    }
}
