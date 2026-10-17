package com.campus.charity.common.constant;

/**
 * 众筹项目状态常量（项目全状态管理）。
 */
public final class ProjectStatusConst {

    /** 草稿（社团编辑中，未提交审核） */
    public static final String DRAFT = "DRAFT";
    /** 待管理员审核 */
    public static final String PENDING = "PENDING";
    /** 审核驳回 */
    public static final String REJECTED = "REJECTED";
    /** 审核通过并上架，对外展示、可捐赠 */
    public static final String ONLINE = "ONLINE";
    /** 管理员下架（违规处置） */
    public static final String OFFLINE = "OFFLINE";
    /** 筹款结束 / 项目执行完毕 */
    public static final String FINISHED = "FINISHED";
    public static final String FINISH_PENDING = "FINISH_PENDING";
    /** 资金已结算 */
    public static final String SETTLED = "SETTLED";

    private ProjectStatusConst() {
    }

    public static String label(String status) {
        if (status == null) {
            return "未知状态";
        }
        switch (status) {
            case DRAFT:
                return "草稿";
            case PENDING:
                return "待审核";
            case REJECTED:
                return "审核驳回";
            case ONLINE:
                return "众筹中";
            case OFFLINE:
                return "已下架";
            case FINISHED:
                return "已完成";
            case FINISH_PENDING:
                return "待结项审核";
            case SETTLED:
                return "已结算";
            default:
                return status;
        }
    }

    /** 未通过审核的项目允许社团编辑修改 */
    public static boolean editable(String status) {
        return DRAFT.equals(status) || REJECTED.equals(status);
    }
}
