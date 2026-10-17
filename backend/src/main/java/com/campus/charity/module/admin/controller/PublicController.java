package com.campus.charity.module.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.charity.common.api.R;
import com.campus.charity.module.admin.entity.Publicity;
import com.campus.charity.module.admin.mapper.PublicityMapper;
import com.campus.charity.module.donation.service.DonationService;
import com.campus.charity.module.project.dto.ProjectDtos;
import com.campus.charity.module.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 校内公开信息接口（登录页展示用）。
 *
 * <p>仅返回已审核上架项目与公示信息，帮扶对象均已脱敏，不包含任何个人隐私。</p>
 */
@Tag(name = "10-校内公开信息", description = "登录页展示的公示与统计概览")
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final ProjectService projectService;
    private final PublicityMapper publicityMapper;
    private final DonationService donationService;

    public PublicController(ProjectService projectService,
                            PublicityMapper publicityMapper,
                            DonationService donationService) {
        this.projectService = projectService;
        this.publicityMapper = publicityMapper;
        this.donationService = donationService;
    }

    @Operation(summary = "已上架公益项目（仅校内展示）")
    @GetMapping("/projects")
    public R<List<ProjectDtos.ProjectVO>> projects(@RequestParam(defaultValue = "6") long size) {
        ProjectDtos.ProjectQuery query = new ProjectDtos.ProjectQuery();
        query.setSize(size);
        query.setSort("hot");
        return R.ok(projectService.pageOnline(query).getRecords());
    }

    @Operation(summary = "公益信息公示")
    @GetMapping("/publicity")
    public R<List<Publicity>> publicity(@RequestParam(defaultValue = "8") long size) {
        return R.ok(publicityMapper.selectList(new LambdaQueryWrapper<Publicity>()
                .eq(Publicity::getStatus, 1)
                .orderByDesc(Publicity::getPublicityId)
                .last("LIMIT " + Math.min(size, 50))));
    }

    @Operation(summary = "平台公益数据概览")
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("donationTotal", donationService.totalAmount());
        map.put("donationCount", donationService.totalCount());
        map.put("projectStatistics", projectService.statistics());
        map.put("note", "本系统仅面向校园内部公益众筹，不面向社会公开募捐");
        return R.ok(map);
    }
}
