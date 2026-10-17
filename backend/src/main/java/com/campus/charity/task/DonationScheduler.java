package com.campus.charity.task;

import com.campus.charity.module.donation.service.DonationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 捐赠定时任务：将超过可取消时限的捐赠置为已入账（确认捐赠）。
 */
@Slf4j
@Component
public class DonationScheduler {

    private final DonationService donationService;

    public DonationScheduler(DonationService donationService) {
        this.donationService = donationService;
    }

    /** 每 5 分钟执行一次 */
    @Scheduled(fixedDelay = 300000L, initialDelay = 60000L)
    public void confirmExpired() {
        try {
            int count = donationService.confirmExpiredDonations();
            if (count > 0) {
                log.info("定时任务已确认 {} 笔捐赠入账", count);
            }
        } catch (Exception e) {
            log.warn("捐赠确认定时任务执行失败：{}", e.getMessage());
        }
    }
}
