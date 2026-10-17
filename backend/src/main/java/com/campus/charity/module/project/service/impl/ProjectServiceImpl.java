package com.campus.charity.module.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.charity.common.annotation.OpLog;
import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.constant.BizTypeConst;
import com.campus.charity.common.constant.CommonConst;
import com.campus.charity.common.constant.ProjectStatusConst;
import com.campus.charity.common.constant.RoleConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.HashUtil;
import com.campus.charity.common.util.MaskUtil;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.fabric.dto.ChainResultDTO;
import com.campus.charity.fabric.dto.EvidencePayload;
import com.campus.charity.module.donation.entity.Donation;
import com.campus.charity.module.donation.mapper.DonationMapper;
import com.campus.charity.module.evidence.service.EvidenceChainService;
import com.campus.charity.module.file.service.FileService;
import com.campus.charity.module.fund.mapper.FundUsageMapper;
import com.campus.charity.module.fund.mapper.FundWithdrawalMapper;
import com.campus.charity.module.notice.service.NotificationService;
import com.campus.charity.module.project.dto.ProjectDtos;
import com.campus.charity.module.project.entity.Project;
import com.campus.charity.module.project.entity.ProjectAudit;
import com.campus.charity.module.project.entity.ProjectFavorite;
import com.campus.charity.module.project.entity.ProjectMaterial;
import com.campus.charity.module.project.entity.ProjectProgress;
import com.campus.charity.module.project.entity.ProjectReview;
import com.campus.charity.module.project.entity.ProjectShareLog;
import com.campus.charity.module.project.mapper.ProjectAuditMapper;
import com.campus.charity.module.project.mapper.ProjectFavoriteMapper;
import com.campus.charity.module.project.mapper.ProjectMapper;
import com.campus.charity.module.project.mapper.ProjectMaterialMapper;
import com.campus.charity.module.project.mapper.ProjectProgressMapper;
import com.campus.charity.module.project.mapper.ProjectReviewMapper;
import com.campus.charity.module.project.mapper.ProjectShareLogMapper;
import com.campus.charity.module.project.service.ProjectService;
import com.campus.charity.module.user.entity.SysUser;
import com.campus.charity.module.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import com.campus.charity.security.BusinessAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 众筹项目管理服务实现。
 */
@Slf4j
@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectAuditMapper auditMapper;
    private final ProjectFavoriteMapper favoriteMapper;
    private final ProjectReviewMapper reviewMapper;
    private final ProjectShareLogMapper shareLogMapper;
    private final ProjectMaterialMapper materialMapper;
    private final ProjectProgressMapper progressMapper;
    private final DonationMapper donationMapper;
    private final FundUsageMapper fundUsageMapper;
    private final FundWithdrawalMapper withdrawalMapper;
    private final UserService userService;
    private final FileService fileService;
    private final EvidenceChainService chainService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final BusinessAccess access;

    public ProjectServiceImpl(ProjectMapper projectMapper,
                              ProjectAuditMapper auditMapper,
                              ProjectFavoriteMapper favoriteMapper,
                              ProjectReviewMapper reviewMapper,
                              ProjectShareLogMapper shareLogMapper,
                              ProjectMaterialMapper materialMapper,
                              ProjectProgressMapper progressMapper,
                              DonationMapper donationMapper,
                              FundUsageMapper fundUsageMapper,
                              FundWithdrawalMapper withdrawalMapper,
                              UserService userService,
                              FileService fileService,
                              EvidenceChainService chainService,
                              NotificationService notificationService,
                              ObjectMapper objectMapper, BusinessAccess access) {
        this.projectMapper = projectMapper;
        this.auditMapper = auditMapper;
        this.favoriteMapper = favoriteMapper;
        this.reviewMapper = reviewMapper;
        this.shareLogMapper = shareLogMapper;
        this.materialMapper = materialMapper;
        this.progressMapper = progressMapper;
        this.donationMapper = donationMapper;
        this.fundUsageMapper = fundUsageMapper;
        this.withdrawalMapper = withdrawalMapper;
        this.userService = userService;
        this.fileService = fileService;
        this.chainService = chainService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper; this.access=access;
    }

    @Override
    public PageResult<ProjectDtos.ProjectVO> pageOnline(ProjectDtos.ProjectQuery query) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Project::getStatus, ProjectStatusConst.ONLINE, ProjectStatusConst.FINISH_PENDING, ProjectStatusConst.FINISHED, ProjectStatusConst.SETTLED);
        applyQuery(wrapper, query);
        applySort(wrapper, query.getSort());
        IPage<Project> page = projectMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return PageResult.of(page, this::toVO);
    }

    @Override
    public ProjectDtos.ProjectDetailVO detail(Long projectId) {
        Project project = getById(projectId);
        // 未上架项目仅发起社团与管理 / 监管人员可见（拦截未审核项目对外展示）
        if (!ProjectStatusConst.ONLINE.equals(project.getStatus())
                && !ProjectStatusConst.FINISH_PENDING.equals(project.getStatus())
                && !ProjectStatusConst.FINISHED.equals(project.getStatus())
                && !ProjectStatusConst.SETTLED.equals(project.getStatus())) {
            Long currentUserId = SecurityUtil.getUserIdOrNull();
            boolean owner = currentUserId != null && currentUserId.equals(project.getClubId());
            if (!owner && !SecurityUtil.isAdmin() && !SecurityUtil.isAuditor()) {
                throw new BizException(ResultCode.PROJECT_NOT_FOUND, "项目未通过审核或已下架");
            }
        }
        projectMapper.increaseViewCount(projectId);
        project.setViewCount(project.getViewCount() == null ? 1 : project.getViewCount() + 1);

        ProjectDtos.ProjectDetailVO vo = new ProjectDtos.ProjectDetailVO();
        copyBase(project, vo);
        vo.setBeneficiaryDesc(project.getBeneficiaryDesc());
        vo.setBeneficiaryContactMasked(project.getBeneficiaryContactMasked());
        vo.setFundsPlan(project.getFundsPlan());
        // include 收藏项目：返回当前用户收藏状态
        vo.setFavorited(isFavorited(projectId, SecurityUtil.getUserIdOrNull()));
        vo.setMyDonationAmount(myDonationAmount(projectId, SecurityUtil.getUserIdOrNull()));
        vo.setFundFlow(fundFlow(projectId));
        vo.setRatingSummary(reviewMapper.selectRatingSummary(projectId));
        vo.setFundUsageList(fundUsageMapper.selectFlowByProject(projectId));
        vo.setProgressList(listProgress(projectId));
        vo.setMaterialList(listMaterials(projectId));
        vo.setAuditList(auditHistory(projectId));
        vo.setReviewList(pageReviews(projectId, 1, 20).getRecords());
        vo.setEvidenceList(chainService.listByProject(projectId).stream()
                .map(this::toEvidenceMap).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public Project getById(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        }
        return project;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "众筹项目管理", value = "社团发起众筹项目")
    public Long create(ProjectDtos.ProjectForm form) {
        Long clubId = SecurityUtil.getUserId();
        SysUser club = userService.getById(clubId);
        if (!RoleConst.CLUB.equals(club.getRole())) {
            throw new BizException(ResultCode.FORBIDDEN, "仅公益社团账号可以发起众筹项目");
        }
        // 社团资质必须审核通过
        if (club.getAuthStatus() == null || club.getAuthStatus() != CommonConst.AUTH_PASSED) {
            throw new BizException(ResultCode.CLUB_NOT_QUALIFIED);
        }
        validateForm(form);

        Project project = new Project();
        project.setProjectNo("PRJ" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase());
        project.setTitle(form.getTitle());
        project.setCategory(StrUtil.blankToDefault(form.getCategory(), "OTHER"));
        project.setBeneficiary(form.getBeneficiary());
        // 帮扶对象脱敏展示（法律约束）
        project.setBeneficiaryMasked(MaskUtil.maskName(form.getBeneficiary()));
        project.setBeneficiaryContactMasked(MaskUtil.maskPhone(form.getBeneficiaryContact()));
        project.setBeneficiaryDesc(form.getBeneficiaryDesc());
        project.setTargetAmount(form.getTargetAmount());
        project.setCurrentAmount(BigDecimal.ZERO);
        project.setStartTime(form.getStartTime());
        project.setEndTime(form.getEndTime());
        project.setStatus(Boolean.FALSE.equals(form.getSubmit()) ? ProjectStatusConst.DRAFT : ProjectStatusConst.PENDING);
        project.setClubId(clubId);
        project.setDescription(form.getDescription());
        project.setFundsPlan(form.getFundsPlan());
        project.setCoverImage(form.getCoverImage());
        project.setDonorCount(0);
        project.setViewCount(0);
        project.setFavoriteCount(0);
        project.setShareCount(0);
        project.setProgressPercent(0);
        project.setDeleted(0);
        project.setCreateTime(LocalDateTime.now());
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.insert(project);

        if (!ProjectStatusConst.DRAFT.equals(project.getStatus())) {
            recordAudit(project.getProjectId(), "SUBMIT", null, "社团提交项目申请，等待平台管理员审核", null);
            notificationService.sendToRole(RoleConst.ADMIN, "新的众筹项目待审核",
                    "社团「" + club.getRealName() + "」提交了《" + project.getTitle() + "》，请及时审核。",
                    "PROJECT_AUDIT", String.valueOf(project.getProjectId()));
        }
        log.info("项目创建成功 projectId={} title={} status={}", project.getProjectId(), project.getTitle(), project.getStatus());
        return project.getProjectId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "众筹项目管理", value = "编辑众筹项目")
    public void update(ProjectDtos.ProjectForm form) {
        Project project = projectMapper.lockById(form.getProjectId());
        if(project == null) throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        checkOwnership(project);
        if (!ProjectStatusConst.editable(project.getStatus())) {
            throw new BizException(ResultCode.PROJECT_STATUS_ERROR, "仅未审核或审核驳回的项目允许编辑修改");
        }
        validateForm(form);
        project.setTitle(form.getTitle());
        project.setCategory(StrUtil.blankToDefault(form.getCategory(), project.getCategory()));
        project.setBeneficiary(form.getBeneficiary());
        project.setBeneficiaryMasked(MaskUtil.maskName(form.getBeneficiary()));
        project.setBeneficiaryContactMasked(MaskUtil.maskPhone(form.getBeneficiaryContact()));
        project.setBeneficiaryDesc(form.getBeneficiaryDesc());
        project.setTargetAmount(form.getTargetAmount());
        project.setStartTime(form.getStartTime());
        project.setEndTime(form.getEndTime());
        project.setDescription(form.getDescription());
        project.setFundsPlan(form.getFundsPlan());
        if (StrUtil.isNotBlank(form.getCoverImage())) {
            project.setCoverImage(form.getCoverImage());
        }
        if (Boolean.TRUE.equals(form.getSubmit()) && ProjectStatusConst.REJECTED.equals(project.getStatus())) {
            project.setStatus(ProjectStatusConst.PENDING);
            recordAudit(project.getProjectId(), "SUBMIT", null, "社团修改后重新提交审核", null);
        }
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);
    }

    @Override
    public void submit(Long projectId) {
        Project project = projectMapper.lockById(projectId);
        if(project == null) throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        checkOwnership(project);
        if (!ProjectStatusConst.DRAFT.equals(project.getStatus()) && !ProjectStatusConst.REJECTED.equals(project.getStatus())) {
            throw new BizException(ResultCode.PROJECT_STATUS_ERROR, "当前状态无需提交审核");
        }
        project.setStatus(ProjectStatusConst.PENDING);
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);
        recordAudit(projectId, "SUBMIT", null, "社团提交项目申请，等待管理员审核", null);
        notificationService.sendToRole(RoleConst.ADMIN, "新的众筹项目待审核",
                "项目《" + project.getTitle() + "》已提交审核。", "PROJECT_AUDIT", String.valueOf(projectId));
    }

    @Override
    public PageResult<ProjectDtos.ProjectVO> pageMyProjects(ProjectDtos.ProjectQuery query) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        Long clubId = SecurityUtil.isAdmin() && query.getClubId() != null ? query.getClubId() : SecurityUtil.getUserId();
        wrapper.eq(Project::getClubId, clubId);
        applyQuery(wrapper, query);
        wrapper.orderByDesc(Project::getProjectId);
        IPage<Project> page = projectMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return PageResult.of(page, this::toVO);
    }

    @Override
    public List<Map<String, Object>> auditHistory(Long projectId) {
        access.requireProject(projectId);
        List<ProjectAudit> audits = auditMapper.selectList(new LambdaQueryWrapper<ProjectAudit>()
                .eq(ProjectAudit::getProjectId, projectId)
                .orderByDesc(ProjectAudit::getAuditId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectAudit audit : audits) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("auditId", audit.getAuditId());
            map.put("auditType", audit.getAuditType());
            map.put("result", audit.getResult());
            map.put("opinion", audit.getOpinion());
            map.put("auditorName", audit.getAuditorName());
            map.put("auditTime", audit.getAuditTime());
            map.put("complianceItems", parseJson(audit.getComplianceItems()));
            result.add(map);
        }
        return result;
    }

    @Override
    public PageResult<ProjectDtos.ProjectVO> pageAll(ProjectDtos.ProjectQuery query) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        applyQuery(wrapper, query);
        if (StrUtil.isNotBlank(query.getStatus())) {
            wrapper.eq(Project::getStatus, query.getStatus());
        }
        if (query.getClubId() != null) {
            wrapper.eq(Project::getClubId, query.getClubId());
        }
        wrapper.orderByDesc(Project::getProjectId);
        IPage<Project> page = projectMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return PageResult.of(page, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "众筹项目管理", value = "管理员审核众筹项目（含合规审查）")
    public void audit(ProjectDtos.ProjectAuditForm form) {
        Project project = projectMapper.lockById(form.getProjectId());
        if(project == null) throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        if (!ProjectStatusConst.PENDING.equals(project.getStatus())) {
            throw new BizException(ResultCode.PROJECT_STATUS_ERROR, "仅待审核项目可以审核");
        }
        if(!"PASS".equals(form.getResult()) && !"REJECT".equals(form.getResult())) throw new BizException(ResultCode.BAD_REQUEST,"审核结果无效");
        if("REJECT".equals(form.getResult()) && StrUtil.isBlank(form.getOpinion())) throw new BizException(ResultCode.BAD_REQUEST,"驳回必须填写审核意见");
        boolean pass = "PASS".equalsIgnoreCase(form.getResult());
        if(pass) {
            for(String item:Arrays.asList("authenticity","compliance","materialComplete","budgetReasonability","beneficiaryVerified"))
                if(form.getComplianceItems()==null || !"PASS".equals(form.getComplianceItems().get(item)))throw new BizException(ResultCode.BAD_REQUEST,"请完成全部项目合规审查项目后再通过审核");
        }
        SysUser admin = userService.getById(SecurityUtil.getUserId());

        project.setAuditUserId(admin.getUserId());
        project.setAuditOpinion(form.getOpinion());
        project.setAuditTime(LocalDateTime.now());

        if (pass) {
            // 审核通过 -> 项目上架对外展示
            project.setStatus(ProjectStatusConst.ONLINE);
            // 项目摘要哈希上链存证
            String dataHash = HashUtil.projectHash(project.getProjectId(), project.getTitle(),
                    project.getTargetAmount(), project.getBeneficiaryMasked(),
                    project.getStartTime(), project.getEndTime());
            ChainResultDTO chainResult = chainService.submit(EvidencePayload.builder()
                    .bizType(BizTypeConst.PROJECT)
                    .bizId(String.valueOf(project.getProjectId()))
                    .projectId(String.valueOf(project.getProjectId()))
                    .dataHash(dataHash)
                    .status(ProjectStatusConst.ONLINE)
                    .operator(admin.getAccount())
                    .operatorRole(RoleConst.ADMIN)
                    .operatorId(admin.getUserId())
                    .operatorName(admin.getRealName())
                    .functionName("CreateProjectEvidence")
                    .build());
            project.setDataHash(dataHash);
            project.setTxId(chainResult.getTxId());
            project.setBlockHeight(chainResult.getBlockHeight());
        } else {
            project.setStatus(ProjectStatusConst.REJECTED);
        }
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);

        Map<String, String> items = form.getComplianceItems();
        recordAudit(project.getProjectId(), pass ? "APPROVE" : "REJECT", pass ? "PASS" : "REJECT",
                form.getOpinion(), items == null ? defaultComplianceItems() : items);

        notificationService.send(project.getClubId(),
                pass ? "项目审核通过" : "项目审核驳回",
                pass ? "您提交的《" + project.getTitle() + "》已通过审核并上线公示，可开始接受捐赠。存证交易ID：" + project.getTxId()
                        : "您提交的《" + project.getTitle() + "》审核未通过，原因："
                        + StrUtil.blankToDefault(form.getOpinion(), "材料不符合要求"),
                "PROJECT_AUDIT", String.valueOf(project.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "后台监管", value = "下架违规项目")
    public void offline(ProjectDtos.OfflineForm form) {
        if(StrUtil.isBlank(form.getReason())) throw new BizException(ResultCode.BAD_REQUEST,"下架必须填写原因");
        Project project = projectMapper.lockById(form.getProjectId());
        if(project == null) throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        if (ProjectStatusConst.OFFLINE.equals(project.getStatus())) {
            throw new BizException(ResultCode.PROJECT_STATUS_ERROR, "项目已处于下架状态");
        }
        SysUser admin = userService.getById(SecurityUtil.getUserId());
        project.setStatus(ProjectStatusConst.OFFLINE);
        project.setOfflineReason(form.getReason());
        project.setUpdateTime(LocalDateTime.now());

        // 监管处置记录上链存证，形成可追溯的处置链路
        LocalDateTime changeTime = LocalDateTime.now();
        String dataHash = HashUtil.projectStatusHash(project.getProjectId(), ProjectStatusConst.OFFLINE, changeTime);
        ChainResultDTO chainResult = chainService.submit(EvidencePayload.builder()
                .bizType(BizTypeConst.PROJECT_STATUS)
                .bizId(String.valueOf(project.getProjectId()))
                .projectId(String.valueOf(project.getProjectId()))
                .dataHash(dataHash)
                .status(ProjectStatusConst.OFFLINE)
                .remark(form.getReason())
                .operator(admin.getAccount())
                .operatorRole(RoleConst.ADMIN)
                .operatorId(admin.getUserId())
                .operatorName(admin.getRealName())
                .functionName("UpdateProjectStatus")
                .build());
        project.setTxId(chainResult.getTxId());
        project.setBlockHeight(chainResult.getBlockHeight());
        projectMapper.updateById(project);

        recordAudit(project.getProjectId(), "OFFLINE", "OFFLINE", form.getReason(), null);
        notificationService.send(project.getClubId(), "项目已下架",
                "您的项目《" + project.getTitle() + "》已被管理员下架，原因："
                        + StrUtil.blankToDefault(form.getReason(), "违规内容"),
                "PROJECT_AUDIT", String.valueOf(project.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void online(Long projectId) {
        Project project = projectMapper.lockById(projectId);
        if(project == null) throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        if(!ProjectStatusConst.OFFLINE.equals(project.getStatus())) throw new BizException(ResultCode.PROJECT_STATUS_ERROR,"仅已下架且曾审核通过的项目可重新上架");
        if(project.getAuditTime()==null) throw new BizException(ResultCode.PROJECT_STATUS_ERROR,"项目必须先通过审核");
        SysUser admin = userService.getById(SecurityUtil.getUserId());
        project.setStatus(ProjectStatusConst.ONLINE);
        project.setOfflineReason(null);
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);
        recordAudit(projectId, "APPROVE", "PASS", "管理员重新上架项目", null);
        notificationService.send(project.getClubId(), "项目已重新上架",
                "您的项目《" + project.getTitle() + "》已由管理员重新上架。", "PROJECT_AUDIT", String.valueOf(projectId));
        log.info("项目重新上架 projectId={} operator={}", projectId, admin.getAccount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long projectId) {
        Project project = projectMapper.lockById(projectId);
        if(project==null) throw new BizException(ResultCode.PROJECT_NOT_FOUND);
        if(SecurityUtil.isClub()) {
            checkOwnership(project);
            if(!ProjectStatusConst.ONLINE.equals(project.getStatus())) throw new BizException(ResultCode.PROJECT_STATUS_ERROR,"仅执行中的项目可申请结项");
            project.setStatus(ProjectStatusConst.FINISH_PENDING);
            projectMapper.updateById(project);
            recordAudit(projectId,"FINISH_APPLY",null,"社团提交结项申请，等待学校审核",null);
        } else if(SecurityUtil.isAdmin()) {
            if(!ProjectStatusConst.FINISH_PENDING.equals(project.getStatus())) throw new BizException(ResultCode.PROJECT_STATUS_ERROR,"需先由社团提交结项申请");
            project.setStatus(ProjectStatusConst.FINISHED);project.setFinishTime(LocalDateTime.now());project.setProgressPercent(100);
            projectMapper.updateById(project);
            recordAudit(projectId,"FINISH","PASS","学校审核通过结项",null);
            chainService.submit(EvidencePayload.builder().bizType(BizTypeConst.PROJECT_STATUS).bizId(String.valueOf(projectId)).projectId(String.valueOf(projectId)).dataHash(HashUtil.projectStatusHash(projectId,ProjectStatusConst.FINISHED,project.getFinishTime())).status(ProjectStatusConst.FINISHED).operatorRole(RoleConst.ADMIN).functionName("UpdateProjectStatus").build());
        } else throw new BizException(ResultCode.FORBIDDEN);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long projectId) {
        Long userId = SecurityUtil.getUserId();
        getById(projectId);
        ProjectFavorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<ProjectFavorite>()
                .eq(ProjectFavorite::getProjectId, projectId)
                .eq(ProjectFavorite::getUserId, userId)
                .last("LIMIT 1"));
        if (favorite != null) {
            favoriteMapper.deleteById(favorite.getFavoriteId());
            projectMapper.increaseFavoriteCount(projectId, -1);
            return false;
        }
        ProjectFavorite entity = new ProjectFavorite();
        entity.setProjectId(projectId);
        entity.setUserId(userId);
        entity.setCreateTime(LocalDateTime.now());
        favoriteMapper.insert(entity);
        projectMapper.increaseFavoriteCount(projectId, 1);
        return true;
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @OpLog(module="项目结项",value="学校审核结项",saveParams=false)
    public void auditFinish(Long projectId,String result,String opinion) {
        Project p=projectMapper.lockById(projectId);
        if(p==null || !ProjectStatusConst.FINISH_PENDING.equals(p.getStatus())) throw new BizException(ResultCode.PROJECT_STATUS_ERROR,"当前项目无待审结项申请");
        if("PASS".equals(result)) {finish(projectId);return;}
        if(!"REJECT".equals(result)||StrUtil.isBlank(opinion))throw new BizException(ResultCode.BAD_REQUEST,"驳回结项必须填写原因");
        p.setStatus(ProjectStatusConst.ONLINE);p.setUpdateTime(LocalDateTime.now());projectMapper.updateById(p);
        recordAudit(projectId,"FINISH_REJECT","REJECT",opinion,null);
        notificationService.send(p.getClubId(),"结项申请已驳回",opinion,"PROJECT_AUDIT",String.valueOf(projectId));
    }

    @Override
    public PageResult<ProjectDtos.ProjectVO> pageMyFavorites(ProjectDtos.ProjectQuery query) {
        Long userId = SecurityUtil.getUserId();
        List<ProjectFavorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<ProjectFavorite>()
                .eq(ProjectFavorite::getUserId, userId)
                .orderByDesc(ProjectFavorite::getFavoriteId));
        if (favorites.isEmpty()) {
            return PageResult.of(new ArrayList<>(), 0, query.getCurrent(), query.getSize());
        }
        List<Long> projectIds = favorites.stream().map(ProjectFavorite::getProjectId).collect(Collectors.toList());
        List<Project> projects = projectMapper.selectBatchIds(projectIds);
        List<ProjectDtos.ProjectVO> records = projects.stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(records, records.size(), query.getCurrent(), query.getSize());
    }

    @Override
    public void share(Long projectId, String channel) {
        getById(projectId);
        ProjectShareLog logEntity = new ProjectShareLog();
        logEntity.setProjectId(projectId);
        logEntity.setUserId(SecurityUtil.getUserIdOrNull());
        logEntity.setChannel(StrUtil.blankToDefault(channel, "LINK"));
        logEntity.setCreateTime(LocalDateTime.now());
        shareLogMapper.insert(logEntity);
        projectMapper.increaseShareCount(projectId);
        log.info("项目分享 projectId={} channel={}", projectId, channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long review(ProjectDtos.ReviewForm form) {
        Long userId = SecurityUtil.getUserId();
        Project project = getById(form.getProjectId());
        // 评价项目需已捐赠（捐赠后可评价）
        long donationCount = donationMapper.selectCount(new LambdaQueryWrapper<Donation>()
                .eq(Donation::getProjectId, form.getProjectId())
                .eq(Donation::getDonorId, userId)
                .eq(Donation::getStatus, CommonConst.DONATION_CONFIRMED).isNotNull(Donation::getConfirmationId).ne(Donation::getRefundStatus, "REFUNDED"));
        if (donationCount <= 0) {
            throw new BizException(ResultCode.BIZ_ERROR, "完成捐赠后才可评价项目");
        }
        ProjectReview review = new ProjectReview();
        review.setProjectId(form.getProjectId());
        review.setUserId(userId);
        review.setDonationId(form.getDonationId());
        review.setRating(form.getRating() == null ? 5 : form.getRating());
        review.setContent(form.getContent());
        review.setAnonymous(form.getAnonymous() == null ? 0 : form.getAnonymous());
        review.setStatus(1);
        review.setDeleted(0);
        review.setCreateTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());
        reviewMapper.insert(review);
        notificationService.send(project.getClubId(), "收到新的项目评价",
                "项目《" + project.getTitle() + "》收到新的捐赠者评价。", "SYSTEM", String.valueOf(form.getProjectId()));
        return review.getReviewId();
    }

    @Override
    public PageResult<Map<String, Object>> pageReviews(Long projectId, long current, long size) {
        IPage<ProjectReview> page = reviewMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ProjectReview>()
                        .eq(ProjectReview::getProjectId, projectId)
                        .eq(ProjectReview::getStatus, 1)
                        .orderByDesc(ProjectReview::getReviewId));
        return PageResult.of(page, review -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("reviewId", review.getReviewId());
            map.put("rating", review.getRating());
            map.put("content", review.getContent());
            map.put("anonymous", review.getAnonymous());
            map.put("createTime", review.getCreateTime());
            map.put("replyContent", review.getReplyContent());
            map.put("replyTime", review.getReplyTime());
            // 评价人脱敏展示
            SysUser user = userService.getById(review.getUserId());
            map.put("reviewer", MaskUtil.donorDisplayName(user.getRealName(),
                    review.getAnonymous() != null && review.getAnonymous() == 1));
            return map;
        });
    }

    @Override
    public void replyReview(ProjectDtos.ReviewReplyForm form) {
        ProjectReview review = reviewMapper.selectById(form.getReviewId());
        if (review == null) {
            throw new BizException(ResultCode.NOT_FOUND, "评价不存在");
        }
        review.setReplyContent(form.getReplyContent());
        review.setReplyTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());
        reviewMapper.updateById(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "区块链存证", value = "提交项目/帮扶证明材料并上链存证")
    public Long uploadMaterial(ProjectDtos.MaterialForm form) {
        Project project = getById(form.getProjectId());
        checkOwnership(project);
        if (StrUtil.isBlank(form.getFileHash()) || !HashUtil.isSha256(form.getFileHash())) {
            throw new BizException(ResultCode.FILE_ERROR, "材料文件哈希无效，请先上传原始文件");
        }
        if(form.getFileId()==null || (!SecurityUtil.isAdmin() && !SecurityUtil.getUserId().equals(fileService.getById(form.getFileId()).getUploaderId())) || !form.getFileHash().equals(fileService.getById(form.getFileId()).getFileHash())) throw new BizException(ResultCode.FORBIDDEN,"请使用本社团上传且哈希一致的材料原件");
        ProjectMaterial material = new ProjectMaterial();
        material.setProjectId(form.getProjectId());
        material.setClubId(SecurityUtil.getUserId());
        material.setMaterialType(StrUtil.blankToDefault(form.getMaterialType(), "PROOF"));
        material.setMaterialName(form.getMaterialName());
        material.setRemark(form.getRemark());
        material.setFileId(form.getFileId());
        material.setFileName(form.getFileName());
        material.setFileUrl(form.getFileUrl());
        material.setFileHash(form.getFileHash());
        material.setFileSize(form.getFileSize());
        material.setUploadTime(LocalDateTime.now());
        material.setOnChain(0);
        material.setDeleted(0);
        material.setCreateTime(LocalDateTime.now());
        material.setUpdateTime(LocalDateTime.now());
        materialMapper.insert(material);

        // extends：材料哈希上链存证
        evidenceMaterial(material, project);
        return material.getMaterialId();
    }

    @Override
    public List<Map<String, Object>> listMaterials(Long projectId) {
        access.requireProject(projectId);
        List<ProjectMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<ProjectMaterial>()
                .eq(ProjectMaterial::getProjectId, projectId)
                .orderByDesc(ProjectMaterial::getMaterialId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectMaterial material : materials) {
            if(SecurityUtil.isStudent() && "BENEFICIARY".equals(material.getMaterialType())) continue;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("materialId", material.getMaterialId());
            map.put("materialType", material.getMaterialType());
            map.put("materialName", material.getMaterialName());
            map.put("remark", material.getRemark());
            map.put("fileId", material.getFileId());
            map.put("fileName", material.getFileName());
            map.put("fileUrl", material.getFileUrl());
            map.put("fileHash", material.getFileHash());
            map.put("onChain", material.getOnChain());
            map.put("txId", material.getTxId());
            map.put("blockHeight", material.getBlockHeight());
            map.put("uploadTime", material.getUploadTime());
            result.add(map);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "众筹项目管理", value = "更新项目进度")
    public Long addProgress(ProjectDtos.ProgressForm form) {
        Project project = getById(form.getProjectId());
        checkOwnership(project);
        ProjectProgress progress = new ProjectProgress();
        progress.setProjectId(form.getProjectId());
        progress.setTitle(form.getTitle());
        progress.setContent(form.getContent());
        progress.setProgressPercent(form.getProgressPercent() == null ? 0 : form.getProgressPercent());
        progress.setFileId(form.getFileId());
        progress.setFileHash(form.getFileHash());
        progress.setCreateUserId(SecurityUtil.getUserId());
        progress.setDeleted(0);
        progress.setCreateTime(LocalDateTime.now());
        progress.setUpdateTime(LocalDateTime.now());
        progressMapper.insert(progress);

        // 进度哈希上链存证
        String dataHash = HashUtil.progressHash(progress.getProgressId(), progress.getProjectId(),
                progress.getProgressPercent(), progress.getTitle());
        SysUser operator = userService.getById(SecurityUtil.getUserId());
        ChainResultDTO chainResult = chainService.submit(EvidencePayload.builder()
                .bizType(BizTypeConst.PROJECT_PROGRESS)
                .bizId(String.valueOf(progress.getProgressId()))
                .projectId(String.valueOf(progress.getProjectId()))
                .dataHash(dataHash)
                .fileHash(form.getFileHash())
                .status(String.valueOf(progress.getProgressPercent()))
                .operator(operator.getAccount())
                .operatorRole(operator.getRole())
                .operatorId(operator.getUserId())
                .operatorName(operator.getRealName())
                .functionName("CreateProjectProgressEvidence")
                .build());
        progress.setDataHash(dataHash);
        progress.setTxId(chainResult.getTxId());
        progress.setBlockHeight(chainResult.getBlockHeight());
        progressMapper.updateById(progress);

        // 同步项目执行进度
        project.setProgressPercent(progress.getProgressPercent());
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);
        return progress.getProgressId();
    }

    @Override
    public List<Map<String, Object>> listProgress(Long projectId) {
        access.requireProject(projectId);
        List<ProjectProgress> progresses = progressMapper.selectList(new LambdaQueryWrapper<ProjectProgress>()
                .eq(ProjectProgress::getProjectId, projectId)
                .orderByDesc(ProjectProgress::getProgressId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectProgress progress : progresses) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("progressId", progress.getProgressId());
            map.put("title", progress.getTitle());
            map.put("content", progress.getContent());
            map.put("progressPercent", progress.getProgressPercent());
            map.put("fileId", progress.getFileId());
            map.put("fileHash", progress.getFileHash());
            map.put("txId", progress.getTxId());
            map.put("blockHeight", progress.getBlockHeight());
            map.put("createTime", progress.getCreateTime());
            result.add(map);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> donorList(Long projectId, int limit) {
        access.requireProject(projectId);
        Project project = getById(projectId);
        if (SecurityUtil.isClub()) {
            checkOwnership(project);
        }
        List<Map<String, Object>> ranks = donationMapper.selectDonorRank(projectId, limit);
        // 捐赠人姓名脱敏展示
        for (Map<String, Object> rank : ranks) {
            String realName = String.valueOf(rank.get("donorName"));
            Object anonymous = rank.get("anonymous");
            boolean isAnonymous = anonymous != null && Integer.parseInt(String.valueOf(anonymous)) == 1;
            rank.put("donorName", MaskUtil.donorDisplayName(realName, isAnonymous));
        }
        return ranks;
    }

    @Override
    public Map<String, Object> fundFlow(Long projectId) {
        access.requireProject(projectId);
        Project project = getById(projectId);
        Map<String, Object> flow = projectMapper.selectFundFlow(projectId);
        if (flow == null) {
            flow = new LinkedHashMap<>();
        }
        flow.put("targetAmount", project.getTargetAmount());
        flow.put("currentAmount", project.getCurrentAmount());
        flow.put("withdrawTotal", withdrawalMapper.sumWithdrawnByProject(projectId));
        flow.put("usageTotal", fundUsageMapper.sumApprovedByProject(projectId));
        flow.put("usageList", fundUsageMapper.selectFlowByProject(projectId));
        flow.put("progressList", listProgress(projectId));
        return flow;
    }

    @Override
    public Map<String, Object> statistics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusDistribution", projectMapper.countByStatus());
        result.put("categoryDistribution", projectMapper.countByCategory());
        result.put("targetAmount", projectMapper.sumTargetAmount());
        result.put("currentAmount", projectMapper.sumCurrentAmount());
        result.put("donationTotal", donationMapper.sumAll());
        result.put("donationCount", donationMapper.countAll());
        result.put("projectCount", projectMapper.selectCount(new LambdaQueryWrapper<>()));
        result.put("trend", donationMapper.selectTrend(30));
        return result;
    }

    @Override
    public List<Map<String, Object>> statusHistory(Long projectId) {
        return auditHistory(projectId);
    }

    // ---------------------------------------------------------------------------------

    private void evidenceMaterial(ProjectMaterial material, Project project) {
        String dataHash = HashUtil.materialHash(material.getMaterialId(), material.getProjectId(),
                material.getMaterialType(), material.getFileHash());
        SysUser operator = userService.getById(SecurityUtil.getUserId());
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("materialType", StrUtil.blankToDefault(material.getMaterialType(), "PROOF"));
        ChainResultDTO chainResult = chainService.submit(EvidencePayload.builder()
                .bizType(BizTypeConst.MATERIAL)
                .bizId(String.valueOf(material.getMaterialId()))
                .projectId(String.valueOf(material.getProjectId()))
                .dataHash(dataHash)
                .fileHash(material.getFileHash())
                .status(material.getMaterialType())
                .operator(operator.getAccount())
                .operatorRole(operator.getRole())
                .operatorId(operator.getUserId())
                .operatorName(operator.getRealName())
                .fileId(material.getFileId())
                .functionName("CreateMaterialEvidence")
                .extra(extra)
                .build());
        material.setDataHash(dataHash);
        material.setTxId(chainResult.getTxId());
        material.setBlockHeight(chainResult.getBlockHeight());
        material.setOnChain(chainResult.getTxId()==null ? 0 : 1);
        materialMapper.updateById(material);
        if (material.getFileId() != null) {
            fileService.markOnChain(material.getFileId(), chainResult.getTxId(), chainResult.getBlockHeight());
        }
    }

    private Map<String, String> defaultComplianceItems() {
        Map<String, String> items = new LinkedHashMap<>();
        items.put("authenticity", "PASS");
        items.put("compliance", "PASS");
        items.put("materialComplete", "PASS");
        items.put("budgetReasonability", "PASS");
        items.put("beneficiaryVerified", "PASS");
        return items;
    }

    private void checkOwnership(Project project) {
        if (SecurityUtil.isAdmin()) {
            return;
        }
        if (!project.getClubId().equals(SecurityUtil.getUserId())) {
            throw new BizException(ResultCode.PROJECT_NOT_OWNED);
        }
    }

    private void validateForm(ProjectDtos.ProjectForm form) {
        if (StrUtil.isBlank(form.getTitle())) {
            throw new BizException(ResultCode.BAD_REQUEST, "项目标题不能为空");
        }
        if (form.getTargetAmount() == null || form.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "请设置有效的筹款目标金额");
        }
        if (form.getStartTime() == null || form.getEndTime() == null || !form.getEndTime().isAfter(form.getStartTime())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请设置有效的项目起止时间");
        }
        if (StrUtil.isBlank(form.getBeneficiary())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写帮扶对象（对外展示时自动脱敏）");
        }
    }

    private void applyQuery(LambdaQueryWrapper<Project> wrapper, ProjectDtos.ProjectQuery query) {
        if (query == null) {
            return;
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(Project::getTitle, query.getKeyword().trim())
                    .or().like(Project::getDescription, query.getKeyword().trim())
                    .or().like(Project::getProjectNo, query.getKeyword().trim()));
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            wrapper.eq(Project::getCategory, query.getCategory());
        }
    }

    private void applySort(LambdaQueryWrapper<Project> wrapper, String sort) {
        if ("hot".equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(Project::getViewCount).orderByDesc(Project::getDonorCount);
        } else if ("progress".equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(Project::getCurrentAmount);
        } else if ("deadline".equalsIgnoreCase(sort)) {
            wrapper.orderByAsc(Project::getEndTime);
        } else {
            wrapper.orderByDesc(Project::getProjectId);
        }
    }

    private void recordAudit(Long projectId, String type, String result, String opinion,
                             Map<String, String> complianceItems) {
        ProjectAudit audit = new ProjectAudit();
        audit.setProjectId(projectId);
        audit.setAuditType(type);
        audit.setResult(result);
        audit.setOpinion(opinion);
        if (SecurityUtil.getUserIdOrNull() != null) {
            audit.setAuditorId(SecurityUtil.getUserId());
            audit.setAuditorName(SecurityUtil.getLoginUser().getRealName());
        }
        audit.setComplianceItems(complianceItems == null ? null : toJson(complianceItems));
        audit.setAuditTime(LocalDateTime.now());
        audit.setNotifyStatus(0);
        audit.setCreateTime(LocalDateTime.now());
        auditMapper.insert(audit);
    }

    private boolean isFavorited(Long projectId, Long userId) {
        if (userId == null) {
            return false;
        }
        return favoriteMapper.selectCount(new LambdaQueryWrapper<ProjectFavorite>()
                .eq(ProjectFavorite::getProjectId, projectId)
                .eq(ProjectFavorite::getUserId, userId)) > 0;
    }

    private String myDonationAmount(Long projectId, Long userId) {
        if (userId == null) {
            return "0.00";
        }
        BigDecimal amount = donationMapper.selectList(new LambdaQueryWrapper<Donation>()
                        .eq(Donation::getProjectId, projectId)
                        .eq(Donation::getDonorId, userId)
                        .eq(Donation::getStatus, CommonConst.DONATION_CONFIRMED).isNotNull(Donation::getConfirmationId).ne(Donation::getRefundStatus, "REFUNDED"))
                .stream().map(Donation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return amount.toPlainString();
    }

    private Map<String, Object> toEvidenceMap(com.campus.charity.module.evidence.entity.EvidenceRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("evidenceId", record.getEvidenceId());
        map.put("evidenceNo", record.getEvidenceNo());
        map.put("bizType", record.getBizType());
        map.put("bizTypeLabel", chainService.bizTypeLabel(record.getBizType()));
        map.put("bizId", record.getBizId());
        map.put("dataHash", record.getDataHash());
        map.put("txId", record.getTxId());
        map.put("blockHeight", record.getBlockHeight());
        map.put("chainTime", record.getChainTime());
        map.put("chainSource", record.getChainSource());
        map.put("status", record.getStatus());
        
        return map;
    }

    private ProjectDtos.ProjectVO toVO(Project project) {
        ProjectDtos.ProjectVO vo = new ProjectDtos.ProjectVO();
        copyBase(project, vo);
        return vo;
    }

    private void copyBase(Project project, ProjectDtos.ProjectVO vo) {
        vo.setProjectId(project.getProjectId());
        vo.setProjectNo(project.getProjectNo());
        vo.setTitle(project.getTitle());
        vo.setCategory(project.getCategory());
        vo.setCategoryLabel(categoryLabel(project.getCategory()));
        // 对外展示一律使用脱敏帮扶对象（法律约束）
        vo.setBeneficiaryMasked(StrUtil.blankToDefault(project.getBeneficiaryMasked(),
                MaskUtil.maskName(project.getBeneficiary())));
        vo.setTargetAmount(project.getTargetAmount());
        vo.setCurrentAmount(project.getCurrentAmount());
        vo.setFundingPercent(percent(project.getCurrentAmount(), project.getTargetAmount()));
        vo.setStartTime(project.getStartTime());
        vo.setEndTime(project.getEndTime());
        vo.setStatus(project.getStatus());
        vo.setStatusLabel(ProjectStatusConst.label(project.getStatus()));
        vo.setClubId(project.getClubId());
        try {
            vo.setClubName(userService.getById(project.getClubId()).getRealName());
        } catch (Exception e) {
            vo.setClubName("公益社团");
        }
        vo.setDescription(project.getDescription());
        vo.setCoverImage(project.getCoverImage());
        vo.setDonorCount(project.getDonorCount());
        vo.setViewCount(project.getViewCount());
        vo.setFavoriteCount(project.getFavoriteCount());
        vo.setShareCount(project.getShareCount());
        vo.setProgressPercent(project.getProgressPercent());
        vo.setDataHash(project.getDataHash());
        vo.setTxId(project.getTxId());
        vo.setBlockHeight(project.getBlockHeight());
        vo.setChainSource(chainService.chainSource());
        vo.setAuditTime(project.getAuditTime());
        vo.setAuditOpinion(project.getAuditOpinion());
        if (project.getEndTime() != null) {
            long days = Duration.between(LocalDateTime.now(), project.getEndTime()).toDays();
            vo.setRemainingDays((int) Math.max(days, 0));
        }
    }

    private int percent(BigDecimal current, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        BigDecimal value = (current == null ? BigDecimal.ZERO : current)
                .multiply(BigDecimal.valueOf(100))
                .divide(target, 0, RoundingMode.HALF_UP);
        return Math.min(value.intValue(), 100);
    }

    private String categoryLabel(String category) {
        if (category == null) {
            return "其他";
        }
        switch (category) {
            case "EDUCATION":
                return "教育帮扶";
            case "MEDICAL":
                return "医疗救助";
            case "POVERTY":
                return "困难帮扶";
            case "DISASTER":
                return "突发救助";
            default:
                return "其他公益";
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Object parseJson(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return json;
        }
    }

    /** 供前端字典使用：项目状态选项 */
    public List<Map<String, Object>> statusOptions() {
        return Arrays.asList(
                option(ProjectStatusConst.DRAFT, ProjectStatusConst.label(ProjectStatusConst.DRAFT)),
                option(ProjectStatusConst.PENDING, ProjectStatusConst.label(ProjectStatusConst.PENDING)),
                option(ProjectStatusConst.REJECTED, ProjectStatusConst.label(ProjectStatusConst.REJECTED)),
                option(ProjectStatusConst.ONLINE, ProjectStatusConst.label(ProjectStatusConst.ONLINE)),
                option(ProjectStatusConst.OFFLINE, ProjectStatusConst.label(ProjectStatusConst.OFFLINE)),
                option(ProjectStatusConst.FINISH_PENDING, ProjectStatusConst.label(ProjectStatusConst.FINISH_PENDING)),
                option(ProjectStatusConst.FINISHED, ProjectStatusConst.label(ProjectStatusConst.FINISHED)),
                option(ProjectStatusConst.SETTLED, ProjectStatusConst.label(ProjectStatusConst.SETTLED)));
    }

    private Map<String, Object> option(String value, String label) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", value);
        map.put("label", label);
        return map;
    }
}
