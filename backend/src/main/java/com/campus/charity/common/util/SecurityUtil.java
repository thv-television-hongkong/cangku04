package com.campus.charity.common.util;

import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.constant.RoleConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 登录用户上下文工具。
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return (LoginUser) authentication.getPrincipal();
    }

    public static LoginUser getLoginUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            return null;
        }
        return (LoginUser) authentication.getPrincipal();
    }

    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    public static Long getUserIdOrNull() {
        LoginUser user = getLoginUserOrNull();
        return user == null ? null : user.getUserId();
    }

    public static String getRole() {
        return getLoginUser().getRole();
    }

    public static boolean isAdmin() {
        LoginUser user = getLoginUserOrNull();
        return user != null && RoleConst.ADMIN.equals(user.getRole());
    }

    public static boolean isClub() {
        LoginUser user = getLoginUserOrNull();
        return user != null && RoleConst.CLUB.equals(user.getRole());
    }

    public static boolean isAuditor() {
        LoginUser user = getLoginUserOrNull();
        return user != null && RoleConst.AUDITOR.equals(user.getRole());
    }

    public static boolean isStudent() {
        LoginUser user = getLoginUserOrNull();
        return user != null && RoleConst.STUDENT.equals(user.getRole());
    }
}
