package com.campus.charity.module.user.service;

import com.campus.charity.common.api.PageResult;
import com.campus.charity.module.user.dto.UserDtos;
import com.campus.charity.module.user.entity.ClubQualification;
import com.campus.charity.module.user.entity.SysUser;

import java.util.List;
import java.util.Map;

/**
 * 用户管理服务：注册登录 / 找回密码 / 个人信息维护 / 角色权限 / 社团资质审核。
 */
public interface UserService {

    /** 发送验证码（注册 / 找回密码） */
    String sendVerifyCode(UserDtos.SendCodeDTO dto);

    /** 注册（学生 / 社团，社团需资质审核） */
    UserDtos.LoginVO register(UserDtos.RegisterDTO dto);

    /** 登录 */
    UserDtos.LoginVO login(UserDtos.LoginDTO dto);

    /** 找回密码（验证码重置） */
    void resetPassword(UserDtos.ResetPasswordDTO dto);

    /** 修改密码 */
    void changePassword(UserDtos.ChangePasswordDTO dto);

    /** 当前登录用户信息 */
    UserDtos.UserVO currentUser();

    /** 个人信息维护 */
    UserDtos.UserVO updateProfile(UserDtos.UpdateProfileDTO dto);

    SysUser getById(Long userId);

    SysUser getByAccount(String account);

    /** 管理员：分页查询用户 */
    PageResult<UserDtos.UserVO> pageUsers(UserDtos.UserQueryDTO query);

    /** 管理员：修改用户状态（冻结 / 解冻） */
    void updateUserStatus(Long userId, Integer status, String remark);

    /** 管理员：权限分配（角色变更） */
    void updateUserRole(Long userId, String role);

    /** 管理员：重置用户密码为默认值 */
    void resetPasswordByAdmin(Long userId);

    /** 管理员：新增系统账号（管理员 / 监管审计） */
    Long createSystemUser(UserDtos.CreateSystemUserDTO dto);

    /** 社团：提交 / 重新提交资质审核 */
    Long submitQualification(UserDtos.QualificationDTO dto);

    /** 管理员：分页查询社团资质 */
    PageResult<ClubQualification> pageQualifications(Integer status, long current, long size);

    /** 管理员：审核社团资质 */
    void auditQualification(UserDtos.QualificationAuditDTO dto);

    /** 当前社团的资质信息 */
    ClubQualification currentQualification(Long userId);

    /** 角色分布统计（数据统计） */
    List<Map<String, Object>> countByRole();

    List<Map<String, Object>> countByMonth();

    /** 用户总数 */
    long countUsers();
}
