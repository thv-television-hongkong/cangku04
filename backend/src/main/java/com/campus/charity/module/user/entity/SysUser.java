package com.campus.charity.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户（ER 实体 1）。
 *
 * <p>用户ID / 账号 / 密码 / 姓名 / 角色 / 学号|社团编号 / 状态。</p>
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long userId;
    /** 登录账号（学号 / 社团编号 / 工号 / 邮箱 / 手机号） */
    private String account;
    /** BCrypt 加密密码，永不明文返回前端 */
    @JsonIgnore
    private String password;
    private String realName;
    /** STUDENT / CLUB / ADMIN / AUDITOR */
    private String role;
    /** 学号 / 社团编号 / 工号 */
    private String identityNo;
    private String phone;
    private String email;
    private String college;
    private String major;
    /** 身份证号哈希（实名认证，不存明文） */
    @JsonIgnore
    private String idCardHash;
    /** 0未提交 1待审核 2已认证 3已驳回 */
    private Integer authStatus;
    /** 内部测试认证标记，恢复人工模式后不具有正式认证权限。 */
    private Integer identityTestOnly;
    /** 1正常 0冻结 2待审核 */
    private Integer status;
    private String avatar;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public boolean isFrozen() {
        return status != null && status == 0;
    }
}
