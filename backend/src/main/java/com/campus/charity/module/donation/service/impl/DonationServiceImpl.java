package com.campus.charity.module.donation.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.charity.common.annotation.OpLog;
import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.config.DonationProperties;
import com.campus.charity.common.constant.BizTypeConst;
import com.campus.charity.common.constant.CommonConst;
import com.campus.charity.common.constant.ProjectStatusConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.HashUtil;
import com.campus.charity.common.util.MaskUtil;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.fabric.dto.ChainResultDTO;
import com.campus.charity.fabric.dto.EvidencePayload;
import com.campus.charity.module.audit.service.WarningService;
import com.campus.charity.module.donation.dto.DonationDtos;
import com.campus.charity.module.donation.entity.Donation;
import com.campus.charity.module.donation.mapper.DonationMapper;
import com.campus.charity.module.donation.service.DonationService;
import com.campus.charity.module.evidence.service.EvidenceChainService;
import com.campus.charity.module.notice.service.NotificationService;
import com.campus.charity.module.project.entity.Project;
import com.campus.charity.module.project.entity.ProjectProgress;
import com.campus.charity.module.project.mapper.ProjectMapper;
import com.campus.charity.module.project.mapper.ProjectProgressMapper;
import com.campus.charity.module.user.entity.SysUser;
import com.campus.charity.module.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 捐赠业务服务实现。
 *
 * <p>完整链路：生成捐赠记录（MySQL）-> 计算捐赠摘要 SHA-256 -> 调用 Fabric 链码
 * CreateDonationEvidence 上链（仅哈希）-> 回写交易ID / 区块高度 / 存证凭证 -> 更新项目筹款。</p>
 */
@Slf4j
@Service
public class DonationServiceImpl implements DonationService {

    private final DonationMapper donationMapper;
    private final ProjectMapper projectMapper;
    private final ProjectProgressMapper progressMapper;
    private final UserService userService;
    private final EvidenceChainService chainService;
    private final WarningService warningService;
    private final NotificationService notificationService;
    private final DonationProperties donationProperties;
    private final org.springframework.jdbc.core.JdbcTemplate db;

    public DonationServiceImpl(DonationMapper donationMapper,
                               ProjectMapper projectMapper,
                               ProjectProgressMapper progressMapper,
                               UserService userService,
                               EvidenceChainService chainService,
                               WarningService warningService,
                               NotificationService notificationService,
                               DonationProperties donationProperties, org.springframework.jdbc.core.JdbcTemplate db) {
        this.donationMapper = donationMapper;
        this.projectMapper = projectMapper;
        this.progressMapper = progressMapper;
        this.userService = userService;
        this.chainService = chainService;
        this.warningService = warningService;
        this.notificationService = notificationService;
        this.donationProperties = donationProperties;
        this.db=db;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "捐赠业务", value = "发起捐赠并上链存证")
    public DonationDtos.DonationVO donate(DonationDtos.DonateForm form) {
        throw new BizException(ResultCode.BAD_REQUEST, "请使用模拟订单并等待学校财务确认；此接口不再直接生成有效捐赠");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "捐赠业务", value = "取消捐赠（限定时间内撤回）")
    public void cancel(DonationDtos.CancelForm form) {
        Donation donation = donationMapper.selectById(form.getDonationId());
        if (donation == null) {
            throw new BizException(ResultCode.DONATION_NOT_FOUND);
        }
        if (!donation.getDonorId().equals(SecurityUtil.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能取消本人的捐赠记录");
        }
        if (CommonConst.DONATION_CONFIRMED.equals(donation.getStatus()) || donation.getConfirmationId() != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "已确认捐赠必须提交退款申请，不能直接取消");
        }
        if (CommonConst.DONATION_CANCELLED.equals(donation.getStatus())) {
            throw new BizException(ResultCode.DONATION_ALREADY_CANCELLED);
        }
        if (donation.getCancelDeadline() == null || LocalDateTime.now().isAfter(donation.getCancelDeadline())) {
            throw new BizException(ResultCode.DONATION_CANCEL_EXPIRED,
                    "已超过可取消时限（捐赠后 " + donationProperties.getCancelWindowMinutes() + " 分钟内可撤回）");
        }

        donation.setStatus(CommonConst.DONATION_CANCELLED);
        donation.setCancelTime(LocalDateTime.now());
        donation.setCancelReason(form.getReason());
        donation.setUpdateTime(LocalDateTime.now());

        // 取消动作同样上链存证，保留历史变更记录（同一业务键的历史可追溯）
        String dataHash = HashUtil.sha256Hex("DONATION_CANCEL|" + donation.getDonationId() + "|"
                + donation.getProjectId() + "|" + donation.getAmount().toPlainString() + "|"
                + HashUtil.TIME_FORMAT.format(donation.getCancelTime()));
        SysUser donor = userService.getById(donation.getDonorId());
        ChainResultDTO chainResult = chainService.submit(EvidencePayload.builder()
                .bizType(BizTypeConst.DONATION)
                .bizId(String.valueOf(donation.getDonationId()))
                .projectId(String.valueOf(donation.getProjectId()))
                .dataHash(dataHash)
                .amount(donation.getAmount().setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString())
                .donorHash(HashUtil.donorHash(donation.getDonorId()))
                .status(CommonConst.DONATION_CANCELLED)
                .remark("捐赠人在限定时间内取消捐赠")
                .operator(donor.getAccount())
                .operatorRole(donor.getRole())
                .operatorId(donor.getUserId())
                .operatorName(donor.getRealName())
                .functionName("CreateDonationEvidence")
                .build());
        donation.setDataHash(dataHash);
        donation.setTxId(chainResult.getTxId());
        donation.setBlockHeight(chainResult.getBlockHeight());
        donationMapper.updateById(donation);

        Project project = projectMapper.selectById(donation.getProjectId());
        if (project != null) {
            notificationService.send(project.getClubId(), "有捐赠被撤回",
                    "项目《" + project.getTitle() + "》有一笔 " + donation.getAmount().toPlainString()
                            + " 元捐赠在限定时间内被捐赠人撤回。", "SYSTEM", String.valueOf(donation.getDonationId()));
        }
        log.info("捐赠已取消 donationId={} txId={}", donation.getDonationId(), donation.getTxId());
    }

    @Override
    public PageResult<DonationDtos.DonationVO> pageMyDonations(DonationDtos.DonationQuery query) {
        LambdaQueryWrapper<Donation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Donation::getDonorId, SecurityUtil.getUserId());
        applyQuery(wrapper, query);
        wrapper.orderByDesc(Donation::getDonationId);
        IPage<Donation> page = donationMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return PageResult.of(page, this::toVOWithProject);
    }

    @Override
    public PageResult<DonationDtos.DonationVO> pageProjectDonations(DonationDtos.DonationQuery query) {
        if (query.getProjectId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请指定项目");
        }
        Project project = projectMapper.selectById(query.getProjectId());
        if (project == null) {
            throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        }
        // 数据权限：社团只能查看本社团项目，捐赠者仅能查看自己的捐赠
        if (SecurityUtil.isClub() && !project.getClubId().equals(SecurityUtil.getUserId())) {
            throw new BizException(ResultCode.PROJECT_NOT_OWNED);
        }
        LambdaQueryWrapper<Donation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Donation::getProjectId, query.getProjectId());
        if (SecurityUtil.isStudent()) {
            wrapper.eq(Donation::getDonorId, SecurityUtil.getUserId());
        }
        applyQuery(wrapper, query);
        wrapper.orderByDesc(Donation::getDonationId);
        IPage<Donation> page = donationMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return PageResult.of(page, this::toVOWithProject);
    }

    @Override
    public DonationDtos.DonationVO detail(Long donationId) {
        Donation donation = donationMapper.selectById(donationId);
        if (donation == null) {
            throw new BizException(ResultCode.DONATION_NOT_FOUND);
        }
        // 严格数据权限：本人 / 项目所属社团 / 管理员 / 监管审计
        Project project = projectMapper.selectById(donation.getProjectId());
        boolean owner = SecurityUtil.getUserIdOrNull() != null
                && (donation.getDonorId().equals(SecurityUtil.getUserId())
                || (project != null && project.getClubId().equals(SecurityUtil.getUserId())));
        if (!owner && !SecurityUtil.isAdmin() && !SecurityUtil.isAuditor()) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return toVOWithProject(donation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmExpiredDonations() {
        return 0; // 历史记录不能按超时自动变为新版财务确认记录
    }

    @Override
    public List<Map<String, Object>> trend(int days) {
        return donationMapper.selectTrend(days);
    }

    @Override
    public BigDecimal totalAmount() {
        BigDecimal total = donationMapper.sumAll();
        return total == null ? BigDecimal.ZERO : total;
    }

    @Override
    public long totalCount() {
        return donationMapper.countAll();
    }

    // ---------------------------------------------------------------------------------

    private void applyQuery(LambdaQueryWrapper<Donation> wrapper, DonationDtos.DonationQuery query) {
        if (query == null) {
            return;
        }
        if (StrUtil.isNotBlank(query.getStatus())) {
            wrapper.eq(Donation::getStatus, query.getStatus());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(Donation::getDonateTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(Donation::getDonateTime, query.getEndTime());
        }
        if (query.getProjectId() != null && "mine".equals(query.getScope())) {
            wrapper.eq(Donation::getProjectId, query.getProjectId());
        }
    }

    private DonationDtos.DonationVO toVOWithProject(Donation donation) {
        Project project = projectMapper.selectById(donation.getProjectId());
        return toVO(donation, project);
    }

    private DonationDtos.DonationVO toVO(Donation donation, Project project) {
        DonationDtos.DonationVO vo = new DonationDtos.DonationVO();
        vo.setDonationId(donation.getDonationId());
        vo.setOrderId(donation.getOrderId());
        vo.setConfirmationId(donation.getConfirmationId());
        vo.setRefundStatus(donation.getRefundStatus());
        List<String> evidenceStates=db.queryForList("SELECT status FROM evidence_task WHERE biz_type='DONATION' AND biz_id=? ORDER BY task_id DESC LIMIT 1",String.class,String.valueOf(donation.getDonationId()));
        vo.setEvidenceStatus(evidenceStates.isEmpty()?(donation.getTxId()==null?"NOT_REQUIRED":"SUBMITTED"):evidenceStates.get(0));
        boolean own=donation.getDonorId().equals(SecurityUtil.getUserIdOrNull());
        String reason=!own?"仅捐赠本人可申请退款":donation.getConfirmationId()==null?"历史记录缺少新版财务确认关联，请联系学校管理人员；可通过新版模拟支付流程体验退款":!"CONFIRMED".equals(donation.getStatus())?"当前捐赠尚未成为有效捐赠":"PROCESSING".equals(donation.getRefundStatus())?"退款处理中，请在退款申请记录中查看进度":"REFUNDED".equals(donation.getRefundStatus())?"该笔捐赠已全额退款":!"NONE".equals(donation.getRefundStatus())?"退款状态异常，请联系学校管理人员":null;
        vo.setRefundEligible(reason==null);vo.setRefundUnavailableReason(reason);
        vo.setDonationNo(donation.getDonationNo());
        vo.setProjectId(donation.getProjectId());
        vo.setProjectTitle(project == null ? "" : project.getTitle());
        vo.setProjectStatus(project == null ? "" : project.getStatus());
        vo.setDonorId(donation.getDonorId());
        vo.setAmount(donation.getAmount());
        vo.setDonateTime(donation.getDonateTime());
        vo.setPayChannel(donation.getPayChannel());
        vo.setStatus(donation.getStatus());
        vo.setStatusLabel(statusLabel(donation.getStatus()));
        vo.setCancelDeadline(donation.getCancelDeadline());
        vo.setCancellable(CommonConst.DONATION_PAID.equals(donation.getStatus())
                && donation.getCancelDeadline() != null
                && LocalDateTime.now().isBefore(donation.getCancelDeadline()));
        vo.setAnonymous(donation.getAnonymous());
        vo.setRemark(donation.getRemark());
        // 存证凭证
        vo.setTxId(donation.getTxId());
        vo.setBlockHeight(donation.getBlockHeight());
        vo.setDataHash(donation.getDataHash());
        vo.setChainSource(chainService.chainSource());
        if (donation.getBlockHeight() != null) {
            vo.setChainTime(donation.getCreateTime());
        }
        // 捐赠人展示名脱敏
        boolean isMine = SecurityUtil.getUserIdOrNull() != null
                && donation.getDonorId().equals(SecurityUtil.getUserId());
        if (isMine || SecurityUtil.isAdmin() || SecurityUtil.isAuditor()) {
            SysUser donor = userService.getById(donation.getDonorId());
            vo.setDonorName(MaskUtil.donorDisplayName(donor.getRealName(),
                    donation.getAnonymous() != null && donation.getAnonymous() == 1));
        } else {
            vo.setDonorName(MaskUtil.donorDisplayName("某某",
                    donation.getAnonymous() == null || donation.getAnonymous() == 1));
        }
        // include 查看项目反馈：项目最新进度
        ProjectProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<ProjectProgress>()
                .eq(ProjectProgress::getProjectId, donation.getProjectId())
                .orderByDesc(ProjectProgress::getProgressId)
                .last("LIMIT 1"));
        if (progress != null) {
            vo.setProjectFeedback(StrUtil.maxLength(progress.getTitle() + "：" + progress.getContent(), 200));
            vo.setProjectProgressTime(progress.getCreateTime());
        }
        return vo;
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case CommonConst.DONATION_PAID:
                return "已支付（可撤回）";
            case CommonConst.DONATION_CONFIRMED:
                return "已入账";
            case CommonConst.DONATION_CANCELLED:
                return "已取消";
            default:
                return status;
        }
    }

    /** 供监管导出：捐赠明细 CSV 数据 */
    public List<Map<String, Object>> exportRows(DonationDtos.DonationQuery query) {
        LambdaQueryWrapper<Donation> wrapper = new LambdaQueryWrapper<>();
        if (query != null && query.getProjectId() != null) {
            wrapper.eq(Donation::getProjectId, query.getProjectId());
        }
        wrapper.orderByDesc(Donation::getDonationId);
        return donationMapper.selectList(wrapper).stream()
                .map(d -> {
                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("捐赠ID", d.getDonationId());
                    map.put("捐赠单号", d.getDonationNo());
                    map.put("项目ID", d.getProjectId());
                    map.put("金额", d.getAmount());
                    map.put("捐赠时间", d.getDonateTime());
                    map.put("状态", statusLabel(d.getStatus()));
                    map.put("交易ID", d.getTxId());
                    map.put("区块高度", d.getBlockHeight());
                    map.put("存证哈希", d.getDataHash());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
