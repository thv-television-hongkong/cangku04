package com.campus.charity.module.fund.service;

import com.campus.charity.common.api.PageResult;
import com.campus.charity.module.fund.dto.FundDtos;
import com.campus.charity.module.fund.entity.FundUsage;
import com.campus.charity.module.fund.entity.FundWithdrawal;

import java.util.Map;

/**
 * 资金服务：资金使用凭证（ER 实体 5）与资金提取。
 *
 * <p>用例：上传资金使用证明（extends 查看存证溯源）、申请资金提取（extends 提交区块链存证）。</p>
 */
public interface FundService {

    /** 上传资金使用凭证（发票 / 收据），文件哈希上链存证 */
    Long uploadUsage(FundDtos.UsageForm form);

    /** 项目资金使用凭证分页 */
    PageResult<Map<String, Object>> pageUsages(Long projectId, Integer auditStatus, long current, long size);

    /** 管理员审核资金使用凭证 */
    void auditUsage(FundDtos.UsageAuditForm form);

    /** 申请资金提取（提取记录哈希上链存证） */
    Long applyWithdrawal(FundDtos.WithdrawalForm form);

    /** 资金提取分页（社团看自己的，管理员 / 监管看全部） */
    PageResult<FundWithdrawal> pageWithdrawals(Long projectId, Integer status, long current, long size);

    /** 管理员审核 / 发放资金提取 */
    void auditWithdrawal(FundDtos.WithdrawalAuditForm form);

    /** 项目资金全景（筹款 / 提取 / 使用 / 结余 / 明细） */
    Map<String, Object> fundOverview(Long projectId);

    FundUsage getUsage(Long usageId);

    FundWithdrawal getWithdrawal(Long withdrawalId);
}
