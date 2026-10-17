package com.campus.charity.module.user.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户模块 DTO / VO 集合。
 *
 * <p>说明：为保持模块内聚，同一模块的传输对象统一定义在本类的静态内部类中。</p>
 */
public class UserDtos {

    /** 发送验证码（注册 / 找回密码） */
    @Data
    public static class SendCodeDTO implements Serializable {
        /** 手机号或邮箱 */
        private String target;
        /** REGISTER / RESET_PASSWORD */
        private String bizType = "RESET_PASSWORD";
    }

    /** 注册（学生 / 社团） */
    @Data
    public static class RegisterDTO implements Serializable {
        private String account;
        private String password;
        private String confirmPassword;
        private String realName;
        /** STUDENT / CLUB */
        private String role;
        private String identityNo;
        private String phone;
        private String email;
        private String college;
        private String major;
        private String code;
        /** 社团注册时提交的资质信息 */
        private String clubName;
        private String clubCode;
        private String advisorName;
        private String advisorPhone;
        private Long licenseFileId;
        private String licenseFileHash;
    }

    /** 登录 */
    @Data
    public static class LoginDTO implements Serializable {
        private String account;
        private String password;
    }

    /** 找回密码 */
    @Data
    public static class ResetPasswordDTO implements Serializable {
        private String account;
        private String code;
        private String newPassword;
        private String confirmPassword;
    }

    /** 修改密码 */
    @Data
    public static class ChangePasswordDTO implements Serializable {
        private String oldPassword;
        private String newPassword;
        private String confirmPassword;
    }

    /** 个人信息维护 */
    @Data
    public static class UpdateProfileDTO implements Serializable {
        private String realName;
        private String phone;
        private String email;
        private String college;
        private String major;
        private String avatar;
        /** 实名认证时提交（后端仅存哈希，不存明文） */
        private String idCardNo;
    }

    /** 用户查询（管理员） */
    @Data
    public static class UserQueryDTO implements Serializable {
        private String keyword;
        private String role;
        private Integer status;
        private long current = 1;
        private long size = 10;
    }

    /** 权限分配 / 状态变更 */
    @Data
    public static class UserUpdateDTO implements Serializable {
        private String role;
        private Integer status;
        private String remark;
    }

    /** 新增系统账号（管理员 / 监管审计由管理员创建） */
    @Data
    public static class CreateSystemUserDTO implements Serializable {
        private String account;
        private String password;
        private String realName;
        /** ADMIN / AUDITOR */
        private String role;
        private String identityNo;
        private String phone;
        private String email;
        private String college;
    }

    /** 社团资质提交 */
    @Data
    public static class QualificationDTO implements Serializable {
        private Long userId;
        private String clubName;
        private String clubCode;
        private String college;
        private String advisorName;
        private String advisorPhone;
        private Long fileId;
        private String licenseHash;
    }

    /** 社团资质审核 */
    @Data
    public static class QualificationAuditDTO implements Serializable {
        private Long qualificationId;
        /** 2 通过 / 3 驳回 */
        private Integer status;
        private String opinion;
    }

    /** 登录返回：Token + 用户信息 */
    @Data
    public static class LoginVO implements Serializable {
        private String token;
        private Long userId;
        private String account;
        private String realName;
        private String role;
        private String roleLabel;
        private String identityNo;
        private Integer authStatus;
        private Boolean identityTestOnly;
        private Boolean identityTestMode;
        private Integer status;
        private List<String> permissions;
    }

    /** 用户信息 */
    @Data
    public static class UserVO implements Serializable {
        private Long userId;
        private String account;
        private String realName;
        private String role;
        private String roleLabel;
        private String identityNo;
        private String identityNoMasked;
        private String phone;
        private String phoneMasked;
        private String email;
        private String emailMasked;
        private String college;
        private String major;
        private Integer authStatus;
        private Boolean identityTestOnly;
        private Boolean identityTestMode;
        private Integer status;
        private String avatar;
        private String remark;
        private LocalDateTime lastLoginTime;
        private LocalDateTime createTime;
    }

    private UserDtos() {
    }
}
