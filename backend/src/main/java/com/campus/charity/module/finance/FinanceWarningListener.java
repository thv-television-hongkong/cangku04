package com.campus.charity.module.finance;

import com.campus.charity.module.audit.service.WarningService;
import com.campus.charity.module.donation.mapper.DonationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 业务提交后在独立事务评估预警，预警组件失败不改变已经完成的财务结果。 */
@Slf4j @Component
public class FinanceWarningListener {
    private final WarningService warnings;
    private final DonationMapper donations;
    public FinanceWarningListener(WarningService warnings, DonationMapper donations) {
        this.warnings=warnings; this.donations=donations;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void confirmed(DonationConfirmed event) {
        try { warnings.evaluateDonation(donations.selectById(event.getDonationId())); }
        catch(Exception e) { log.error("财务确认后预警评估失败 donationId={} type={}",event.getDonationId(),e.getClass().getSimpleName()); }
    }
}
