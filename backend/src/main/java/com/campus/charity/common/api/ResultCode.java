package com.campus.charity.common.api;

import lombok.Getter;

/**
 * 统一业务状态码。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问该功能"),
    NOT_FOUND(404, "资源不存在"),
    BIZ_ERROR(500, "业务处理失败"),

    // 用户模块
    ACCOUNT_EXISTS(1001, "账号已存在"),
    ACCOUNT_NOT_FOUND(1002, "账号不存在"),
    PASSWORD_ERROR(1003, "账号或密码错误"),
    ACCOUNT_DISABLED(1004, "账号已被冻结或停用"),
    CODE_ERROR(1005, "验证码错误或已过期"),
    CLUB_NOT_QUALIFIED(1006, "社团资质未通过审核，暂不能发起项目"),

    // 项目模块
    PROJECT_NOT_FOUND(2001, "项目不存在"),
    PROJECT_STATUS_ERROR(2002, "当前项目状态不允许该操作"),
    PROJECT_NOT_OWNED(2003, "只能操作本社团发起的项目"),
    PROJECT_NOT_ONLINE(2004, "项目未上架，无法捐赠"),

    // 捐赠模块
    DONATION_NOT_FOUND(3001, "捐赠记录不存在"),
    DONATION_CANCEL_EXPIRED(3002, "已超过可取消时限，无法取消捐赠"),
    DONATION_ALREADY_CANCELLED(3003, "该捐赠已取消"),

    // 存证 / 溯源
    CHAIN_ERROR(4001, "区块链存证调用失败"),
    EVIDENCE_NOT_FOUND(4002, "链上存证不存在"),
    HASH_MISMATCH(4003, "哈希不一致，文件可能已被篡改"),
    PRIVACY_REJECTED(4004, "存证载荷包含敏感隐私信息，链上仅允许哈希摘要"),
    FILE_ERROR(4005, "文件处理失败"),

    // 监管审计
    RULE_NOT_FOUND(5001, "预警规则不存在"),
    REPORT_ERROR(5002, "审计报告生成失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
