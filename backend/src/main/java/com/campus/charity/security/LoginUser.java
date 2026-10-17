package com.campus.charity.security;

import com.campus.charity.common.constant.CommonConst;
import com.campus.charity.common.constant.RoleConst;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 登录用户（Spring Security 上下文对象）。
 */
@Data
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String account;
    private String realName;
    /** STUDENT / CLUB / ADMIN / AUDITOR */
    private String role;
    /** 学号 / 社团编号 / 工号 */
    private String identityNo;
    /** 账号状态：1 正常 / 0 冻结 / 2 待审核 */
    private Integer status = CommonConst.USER_NORMAL;
    /** 实名 / 资质认证状态 */
    private Integer authStatus = CommonConst.AUTH_NONE;

    @JsonIgnore
    private String password;

    public boolean isClub() {
        return RoleConst.CLUB.equals(role);
    }

    public boolean isAdmin() {
        return RoleConst.ADMIN.equals(role);
    }

    public boolean isAuditor() {
        return RoleConst.AUDITOR.equals(role);
    }

    public boolean isStudent() {
        return RoleConst.STUDENT.equals(role);
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return account;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return status != null && status != CommonConst.USER_FROZEN;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return status != null && status == CommonConst.USER_NORMAL;
    }
}
