package com.campus.charity.common.constant;

/**
 * 链上存证业务类型常量（与 Go 链码 contract/model.go 中的 BizType 一一对应）。
 */
public final class BizTypeConst {

    /** 众筹项目摘要哈希 */
    public static final String PROJECT = "PROJECT";
    /** 捐赠记录哈希 */
    public static final String DONATION = "DONATION";
    public static final String REFUND = "REFUND";
    /** 资金使用（报销）凭证哈希 */
    public static final String FUND_USAGE = "FUND_USAGE";
    /** 项目 / 帮扶证明材料哈希 */
    public static final String MATERIAL = "MATERIAL";
    /** 资金提取记录哈希 */
    public static final String WITHDRAWAL = "WITHDRAWAL";
    /** 项目进度哈希 */
    public static final String PROJECT_PROGRESS = "PROJECT_PROGRESS";
    /** 审计报告哈希（监管方生成报告后存证） */
    public static final String AUDIT_REPORT = "AUDIT_REPORT";
    /** 项目状态变更（上架 / 下架 / 结项）存证 */
    public static final String PROJECT_STATUS = "PROJECT_STATUS";

    private BizTypeConst() {
    }

    public static String label(String bizType) {
        if (bizType == null) {
            return "未知业务";
        }
        switch (bizType) {
            case PROJECT:
                return "众筹项目";
            case DONATION:
                return "捐赠记录";
            case REFUND:
                return "退款完成记录";
            case FUND_USAGE:
                return "资金使用凭证";
            case MATERIAL:
                return "帮扶/项目材料";
            case WITHDRAWAL:
                return "资金提取";
            case PROJECT_PROGRESS:
                return "项目进度";
            case AUDIT_REPORT:
                return "审计报告";
            case PROJECT_STATUS:
                return "项目状态变更";
            default:
                return bizType;
        }
    }
}
