package com.campus.charity.module.donation.controller;

import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.R;
import com.campus.charity.module.donation.dto.DonationDtos;
import com.campus.charity.module.donation.service.DonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 捐赠接口：捐款 / 捐赠、取消捐赠、查看捐赠记录、项目捐赠明细。
 */
@Tag(name = "05-捐赠业务", description = "捐赠 / 取消捐赠 / 捐赠记录")
@RestController
@RequestMapping("/api/donations")
public class DonationController {

    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    @Operation(summary = "捐款 / 捐赠（上链存证，返回交易ID与区块高度）")
    @PostMapping
    public R<DonationDtos.DonationVO> donate(@RequestBody DonationDtos.DonateForm form) {
        return R.ok("捐赠成功，捐赠记录已上链存证", donationService.donate(form));
    }

    @Operation(summary = "取消捐赠（extends：限定时间内取消捐赠）")
    @PostMapping("/cancel")
    public R<Void> cancel(@RequestBody DonationDtos.CancelForm form) {
        donationService.cancel(form);
        return R.ok("捐赠已取消，撤回记录已上链存证", null);
    }

    @Operation(summary = "我的捐赠记录（include 查看项目反馈）")
    @GetMapping("/mine")
    public R<PageResult<DonationDtos.DonationVO>> myDonations(DonationDtos.DonationQuery query) {
        return R.ok(donationService.pageMyDonations(query));
    }

    @Operation(summary = "项目捐赠明细（社团 / 管理员 / 监管）")
    @GetMapping("/project")
    public R<PageResult<DonationDtos.DonationVO>> projectDonations(DonationDtos.DonationQuery query) {
        return R.ok(donationService.pageProjectDonations(query));
    }

    @Operation(summary = "捐赠详情（含存证凭证与项目反馈）")
    @GetMapping("/{donationId}")
    public R<DonationDtos.DonationVO> detail(@PathVariable Long donationId) {
        return R.ok(donationService.detail(donationId));
    }
}
