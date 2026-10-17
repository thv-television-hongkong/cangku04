package com.campus.charity.module.finance;

/** 只在财务首次创建有效捐赠时发布，幂等重放不重复发布。 */
@lombok.Value
public class DonationConfirmed { Long donationId; }
