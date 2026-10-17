package com.campus.charity.module.donation.service;

import com.campus.charity.common.api.PageResult;
import com.campus.charity.module.donation.dto.DonationDtos;

import java.util.List;
import java.util.Map;

/**
 * 捐赠业务服务。
 *
 * <p>用例：捐款 / 捐赠（extends 取消捐赠）、查看捐赠记录（include 查看项目反馈）。</p>
 */
public interface DonationService {

    /** 发起捐赠：生成捐赠记录 -> 哈希上链 -> 返回交易ID / 区块高度存证凭证 */
    DonationDtos.DonationVO donate(DonationDtos.DonateForm form);

    /** 取消捐赠（限定时间内可撤回，撤回记录同步上链） */
    void cancel(DonationDtos.CancelForm form);

    /** 我的捐赠记录（含存证凭证与项目反馈） */
    PageResult<DonationDtos.DonationVO> pageMyDonations(DonationDtos.DonationQuery query);

    /** 项目捐赠明细（社团 / 管理员 / 监管） */
    PageResult<DonationDtos.DonationVO> pageProjectDonations(DonationDtos.DonationQuery query);

    /** 捐赠详情 */
    DonationDtos.DonationVO detail(Long donationId);

    /** 定时任务：将超过可取消时限的捐赠置为已入账（CONFIRMED） */
    int confirmExpiredDonations();

    /** 捐赠趋势统计 */
    List<Map<String, Object>> trend(int days);

    /** 平台捐赠总额 */
    java.math.BigDecimal totalAmount();

    /** 平台捐赠笔数 */
    long totalCount();
}
