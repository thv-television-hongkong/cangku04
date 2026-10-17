package com.campus.charity.common.constant;

/**
 * 业务常量：捐赠状态 / 审核状态 / 预警级别等。
 */
public final class CommonConst {

    /** 捐赠——已支付（在可取消窗口内可撤回） */
    public static final String DONATION_PAID = "PAID";
    /** 捐赠——已入账（不可撤回） */
    public static final String DONATION_CONFIRMED = "CONFIRMED";
    /** 捐赠——已取消 */
    public static final String DONATION_CANCELLED = "CANCELLED";

    /** 审核状态：待审核 */
    public static final int AUDIT_PENDING = 0;
    /** 审核状态：已通过 */
    public static final int AUDIT_PASSED = 1;
    /** 审核状态：已驳回 */
    public static final int AUDIT_REJECTED = 2;

    /** 账号状态：冻结 */
    public static final int USER_FROZEN = 0;
    /** 账号状态：正常 */
    public static final int USER_NORMAL = 1;
    /** 账号状态：待审核 */
    public static final int USER_PENDING = 2;

    /** 实名 / 资质认证：未提交 */
    public static final int AUTH_NONE = 0;
    /** 待审核 */
    public static final int AUTH_PENDING = 1;
    /** 已认证 */
    public static final int AUTH_PASSED = 2;
    /** 已驳回 */
    public static final int AUTH_REJECTED = 3;

    /** 预警级别 */
    public static final String WARNING_LOW = "LOW";
    public static final String WARNING_MID = "MID";
    public static final String WARNING_HIGH = "HIGH";

    /** 操作日志结果 */
    public static final String LOG_SUCCESS = "SUCCESS";
    public static final String LOG_FAIL = "FAIL";

    /** 链上存证来源 */
    public static final String CHAIN_FABRIC = "FABRIC";
    public static final String CHAIN_MOCK = "MOCK";

    private CommonConst() {
    }
}
