package com.campus.charity.common.constant;

/**
 * 系统角色常量。
 *
 * <p>需求：角色权限区分（学生 / 社团 / 管理员 / 监管审计）；社团账号需要资质审核。</p>
 */
public final class RoleConst {

    /** 普通学生（捐赠者） */
    public static final String STUDENT = "STUDENT";
    /** 公益社团（项目发起方 / 受助者） */
    public static final String CLUB = "CLUB";
    /** 学校平台管理员 */
    public static final String ADMIN = "ADMIN";
    /** 监管审计方 */
    public static final String AUDITOR = "AUDITOR";

    private RoleConst() {
    }

    public static String label(String role) {
        if (role == null) {
            return "未知角色";
        }
        switch (role) {
            case STUDENT:
                return "普通学生";
            case CLUB:
                return "公益社团";
            case ADMIN:
                return "平台管理员";
            case AUDITOR:
                return "监管审计";
            default:
                return role;
        }
    }

    public static boolean isValid(String role) {
        return STUDENT.equals(role) || CLUB.equals(role) || ADMIN.equals(role) || AUDITOR.equals(role);
    }
}
