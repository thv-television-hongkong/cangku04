package com.campus.charity.module.user.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.charity.common.api.PageResult;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.constant.CommonConst;
import com.campus.charity.common.constant.RoleConst;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.HashUtil;
import com.campus.charity.common.util.MaskUtil;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.common.util.WebUtil;
import com.campus.charity.module.notice.service.NotificationService;
import com.campus.charity.module.user.dto.UserDtos;
import com.campus.charity.module.user.entity.ClubQualification;
import com.campus.charity.module.user.entity.SysUser;
import com.campus.charity.module.user.entity.VerifyCode;
import com.campus.charity.module.user.mapper.ClubQualificationMapper;
import com.campus.charity.module.user.mapper.SysUserMapper;
import com.campus.charity.module.user.mapper.VerifyCodeMapper;
import com.campus.charity.module.user.service.UserService;
import com.campus.charity.security.JwtUtil;
import com.campus.charity.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现。
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    /** 默认重置密码（首次登录后建议修改） */
    private static final String DEFAULT_PASSWORD = "123456";
    private static final int CODE_EXPIRE_MINUTES = 5;

    private final SysUserMapper userMapper;
    private final VerifyCodeMapper verifyCodeMapper;
    private final ClubQualificationMapper qualificationMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;
    private final com.campus.charity.module.user.service.CampusIdentityPolicy identities;

    public UserServiceImpl(SysUserMapper userMapper,
                           VerifyCodeMapper verifyCodeMapper,
                           ClubQualificationMapper qualificationMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           NotificationService notificationService, com.campus.charity.module.user.service.CampusIdentityPolicy identities) {
        this.userMapper = userMapper;
        this.verifyCodeMapper = verifyCodeMapper;
        this.qualificationMapper = qualificationMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.notificationService = notificationService;
        this.identities = identities;
    }

    @Override
    public String sendVerifyCode(UserDtos.SendCodeDTO dto) {
        if (StrUtil.isBlank(dto.getTarget())) {
            throw new BizException(ResultCode.BAD_REQUEST, "手机号 / 邮箱不能为空");
        }
        String code = RandomUtil.randomNumbers(6);
        VerifyCode entity = new VerifyCode();
        entity.setTarget(dto.getTarget().trim());
        entity.setCode(code);
        entity.setBizType(StrUtil.blankToDefault(dto.getBizType(), "RESET_PASSWORD"));
        entity.setExpireTime(LocalDateTime.now().plusMinutes(CODE_EXPIRE_MINUTES));
        entity.setUsed(0);
        entity.setSendIp(WebUtil.getClientIp());
        entity.setCreateTime(LocalDateTime.now());
        verifyCodeMapper.insert(entity);
        // 校内系统未接入短信 / 邮件网关，验证码在此返回并写入日志，便于校内演示与测试；
        // 正式部署时应接入学校统一消息平台后仅发送不返回。
        log.info("演示验证码已生成 bizType={}",entity.getBizType());
        return code;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDtos.LoginVO register(UserDtos.RegisterDTO dto) {
        if (StrUtil.isBlank(dto.getAccount()) || StrUtil.isBlank(dto.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "账号与密码不能为空");
        }
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        String role = StrUtil.blankToDefault(dto.getRole(), RoleConst.STUDENT).toUpperCase();
        if (!RoleConst.STUDENT.equals(role) && !RoleConst.CLUB.equals(role)) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅支持注册普通学生或公益社团账号，管理类账号由管理员创建");
        }
        if (countByAccount(dto.getAccount()) > 0) {
            throw new BizException(ResultCode.ACCOUNT_EXISTS);
        }
        // 验证码校验（校内演示环境允许使用固定码 888888）
        verifyCode(dto.getAccount(), dto.getCode(), "REGISTER");

        SysUser user = new SysUser();
        user.setAccount(dto.getAccount().trim());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(StrUtil.blankToDefault(dto.getRealName(), dto.getAccount()));
        user.setRole(role);
        user.setIdentityNo(dto.getIdentityNo());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setCollege(dto.getCollege());
        user.setMajor(dto.getMajor());
        // 社团账号需要资质审核，未通过前状态为待审核
        user.setStatus(RoleConst.CLUB.equals(role) ? CommonConst.USER_PENDING : CommonConst.USER_NORMAL);
        user.setAuthStatus(CommonConst.AUTH_PENDING);
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);

        if (RoleConst.CLUB.equals(role)) {
            UserDtos.QualificationDTO qualification = new UserDtos.QualificationDTO();
            qualification.setUserId(user.getUserId());
            qualification.setClubName(StrUtil.blankToDefault(dto.getClubName(), dto.getRealName()));
            qualification.setClubCode(StrUtil.blankToDefault(dto.getClubCode(), dto.getIdentityNo()));
            qualification.setCollege(dto.getCollege());
            qualification.setAdvisorName(dto.getAdvisorName());
            qualification.setAdvisorPhone(dto.getAdvisorPhone());
            qualification.setFileId(dto.getLicenseFileId());
            qualification.setLicenseHash(dto.getLicenseFileHash());
            submitQualification(qualification);
            notificationService.send(user.getUserId(), "社团账号已提交",
                    "您的社团账号已注册成功，资质材料已提交平台管理员审核，审核通过后即可发起众筹项目。",
                    "SYSTEM", String.valueOf(user.getUserId()));
        }
        log.info("用户注册成功 userId={} role={}", user.getUserId(), role);
        return buildLoginVO(user);
    }

    @Override
    public UserDtos.LoginVO login(UserDtos.LoginDTO dto) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getAccount, dto.getAccount()).last("LIMIT 1"));
        if (user == null) {
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() == CommonConst.USER_FROZEN) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED, "账号已被冻结，请联系平台管理员");
        }
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(WebUtil.getClientIp());
        userMapper.updateById(user);
        return buildLoginVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(UserDtos.ResetPasswordDTO dto) {
        SysUser user = getByAccount(dto.getAccount());
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        verifyCode(dto.getAccount(), dto.getCode(), "RESET_PASSWORD");
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("用户重置密码成功 userId={}", user.getUserId());
    }

    @Override
    public void changePassword(UserDtos.ChangePasswordDTO dto) {
        SysUser user = getById(SecurityUtil.getUserId());
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR, "原密码不正确");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public UserDtos.UserVO currentUser() {
        return toVO(getById(SecurityUtil.getUserId()));
    }

    @Override
    public UserDtos.UserVO updateProfile(UserDtos.UpdateProfileDTO dto) {
        SysUser user = getById(SecurityUtil.getUserId());
        if (StrUtil.isNotBlank(dto.getRealName())) {
            user.setRealName(dto.getRealName());
        }
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setCollege(dto.getCollege());
        user.setMajor(dto.getMajor());
        if (StrUtil.isNotBlank(dto.getAvatar())) {
            user.setAvatar(dto.getAvatar());
        }
        // 实名认证：仅保存身份证号的哈希，不存明文（法律约束）
        if (StrUtil.isNotBlank(dto.getIdCardNo())) {
            user.setIdCardHash(HashUtil.idCardHash(dto.getIdCardNo()));
            if (user.getAuthStatus() == null || user.getAuthStatus() != CommonConst.AUTH_PASSED) {
                user.setAuthStatus(CommonConst.AUTH_PENDING);
            }
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return toVO(user);
    }

    @Override
    public SysUser getById(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        return user;
    }

    @Override
    public SysUser getByAccount(String account) {
        if (StrUtil.isBlank(account)) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getAccount, account.trim()).last("LIMIT 1"));
    }

    @Override
    public PageResult<UserDtos.UserVO> pageUsers(UserDtos.UserQueryDTO query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysUser::getAccount, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or().like(SysUser::getIdentityNo, keyword));
        }
        if (StrUtil.isNotBlank(query.getRole())) {
            wrapper.eq(SysUser::getRole, query.getRole());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(SysUser::getUserId);
        IPage<SysUser> page = userMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return PageResult.of(page, this::toVO);
    }

    @Override
    public void updateUserStatus(Long userId, Integer status, String remark) {
        SysUser user = getById(userId);
        if (RoleConst.ADMIN.equals(user.getRole()) && status != null && status == CommonConst.USER_FROZEN) {
            long adminCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRole, RoleConst.ADMIN).eq(SysUser::getStatus, CommonConst.USER_NORMAL));
            if (adminCount <= 1) {
                throw new BizException(ResultCode.BIZ_ERROR, "系统至少需要保留一个可用的管理员账号");
            }
        }
        user.setStatus(status);
        if (StrUtil.isNotBlank(remark)) {
            user.setRemark(remark);
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        notificationService.send(userId,
                status != null && status == CommonConst.USER_FROZEN ? "账号已冻结" : "账号已启用",
                StrUtil.blankToDefault(remark, "您的账号状态已由平台管理员更新。"), "SYSTEM", String.valueOf(userId));
    }

    @Override
    public void updateUserRole(Long userId, String role) {
        if (!RoleConst.isValid(role)) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法的角色类型");
        }
        SysUser user = getById(userId);
        user.setRole(role);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        notificationService.send(userId, "权限已变更",
                "您的账号角色已被管理员调整为：" + RoleConst.label(role), "SYSTEM", String.valueOf(userId));
    }

    @Override
    public void resetPasswordByAdmin(Long userId) {
        SysUser user = getById(userId);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        notificationService.send(userId, "密码已重置",
                "您的登录密码已被管理员重置为默认密码，请登录后立即修改。", "SYSTEM", String.valueOf(userId));
    }

    @Override
    public Long createSystemUser(UserDtos.CreateSystemUserDTO dto) {
        if (!RoleConst.ADMIN.equals(dto.getRole()) && !RoleConst.AUDITOR.equals(dto.getRole())) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅可创建管理员或监管审计账号");
        }
        if (countByAccount(dto.getAccount()) > 0) {
            throw new BizException(ResultCode.ACCOUNT_EXISTS);
        }
        SysUser user = new SysUser();
        user.setAccount(dto.getAccount());
        user.setPassword(passwordEncoder.encode(StrUtil.blankToDefault(dto.getPassword(), DEFAULT_PASSWORD)));
        user.setRealName(dto.getRealName());
        user.setRole(dto.getRole());
        user.setIdentityNo(dto.getIdentityNo());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setCollege(dto.getCollege());
        user.setStatus(CommonConst.USER_NORMAL);
        user.setAuthStatus(CommonConst.AUTH_PASSED);
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user.getUserId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitQualification(UserDtos.QualificationDTO dto) {
        Long userId = dto.getUserId() == null ? SecurityUtil.getUserId() : dto.getUserId();
        SysUser user = getById(userId);
        if (!RoleConst.CLUB.equals(user.getRole())) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅公益社团账号需要提交资质审核");
        }
        ClubQualification qualification = new ClubQualification();
        qualification.setUserId(userId);
        qualification.setClubName(dto.getClubName());
        qualification.setClubCode(StrUtil.blankToDefault(dto.getClubCode(), user.getIdentityNo()));
        qualification.setCollege(dto.getCollege());
        qualification.setAdvisorName(dto.getAdvisorName());
        qualification.setAdvisorPhone(dto.getAdvisorPhone());
        qualification.setFileId(dto.getFileId());
        qualification.setLicenseHash(dto.getLicenseHash());
        qualification.setStatus(CommonConst.AUTH_PENDING);
        qualification.setCreateTime(LocalDateTime.now());
        qualification.setUpdateTime(LocalDateTime.now());
        qualificationMapper.insert(qualification);

        user.setAuthStatus(CommonConst.AUTH_PENDING);
        user.setStatus(CommonConst.USER_PENDING);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return qualification.getQualificationId();
    }

    @Override
    public PageResult<ClubQualification> pageQualifications(Integer status, long current, long size) {
        LambdaQueryWrapper<ClubQualification> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ClubQualification::getStatus, status);
        }
        wrapper.orderByDesc(ClubQualification::getQualificationId);
        IPage<ClubQualification> page = qualificationMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditQualification(UserDtos.QualificationAuditDTO dto) {
        ClubQualification qualification = qualificationMapper.selectById(dto.getQualificationId());
        if (qualification == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资质记录不存在");
        }
        qualification.setStatus(dto.getStatus());
        qualification.setAuditOpinion(dto.getOpinion());
        qualification.setAuditUserId(SecurityUtil.getUserId());
        qualification.setAuditTime(LocalDateTime.now());
        qualification.setUpdateTime(LocalDateTime.now());
        qualificationMapper.updateById(qualification);

        SysUser user = getById(qualification.getUserId());
        boolean passed = dto.getStatus() != null && dto.getStatus() == 2;
        user.setAuthStatus(passed ? CommonConst.AUTH_PASSED : CommonConst.AUTH_REJECTED);
        user.setStatus(passed ? CommonConst.USER_NORMAL : CommonConst.USER_PENDING);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        notificationService.send(user.getUserId(), passed ? "社团资质审核通过" : "社团资质审核未通过",
                passed ? "恭喜，您的社团资质已通过审核，现在可以发起众筹项目。"
                        : "您的社团资质审核未通过，原因：" + StrUtil.blankToDefault(dto.getOpinion(), "材料不完整"),
                "SYSTEM", String.valueOf(qualification.getQualificationId()));
    }

    @Override
    public ClubQualification currentQualification(Long userId) {
        return qualificationMapper.selectOne(new LambdaQueryWrapper<ClubQualification>()
                .eq(ClubQualification::getUserId, userId)
                .orderByDesc(ClubQualification::getQualificationId)
                .last("LIMIT 1"));
    }

    @Override
    public List<Map<String, Object>> countByRole() {
        return userMapper.countByRole();
    }

    @Override
    public List<Map<String, Object>> countByMonth() {
        return userMapper.countByMonth();
    }

    @Override
    public long countUsers() {
        return userMapper.selectCount(new LambdaQueryWrapper<>());
    }

    // ---------------------------------------------------------------------------------

    private long countByAccount(String account) {
        if (StrUtil.isBlank(account)) {
            return 0;
        }
        return userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getAccount, account.trim()));
    }

    private void verifyCode(String target, String code, String bizType) {
        VerifyCode entity = verifyCodeMapper.selectOne(new LambdaQueryWrapper<VerifyCode>()
                .eq(VerifyCode::getTarget, target)
                .eq(VerifyCode::getBizType, bizType)
                .eq(VerifyCode::getUsed, 0)
                .orderByDesc(VerifyCode::getCodeId)
                .last("LIMIT 1"));
        if (entity == null || !entity.getCode().equals(code) || entity.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.CODE_ERROR);
        }
        entity.setUsed(1);
        verifyCodeMapper.updateById(entity);
    }

    private UserDtos.LoginVO buildLoginVO(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setAccount(user.getAccount());
        loginUser.setRealName(user.getRealName());
        loginUser.setRole(user.getRole());
        loginUser.setIdentityNo(user.getIdentityNo());
        loginUser.setStatus(user.getStatus());
        loginUser.setAuthStatus(identities.effectiveStatus(user));

        UserDtos.LoginVO vo = new UserDtos.LoginVO();
        vo.setToken(jwtUtil.generateToken(loginUser));
        vo.setUserId(user.getUserId());
        vo.setAccount(user.getAccount());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        vo.setRoleLabel(RoleConst.label(user.getRole()));
        vo.setIdentityNo(user.getIdentityNo());
        vo.setAuthStatus(identities.effectiveStatus(user));
        vo.setIdentityTestOnly(Integer.valueOf(1).equals(user.getIdentityTestOnly()));
        vo.setIdentityTestMode(identities.isTestMode());
        vo.setStatus(user.getStatus());
        vo.setPermissions(buildPermissions(user.getRole()));
        return vo;
    }

    /** 角色可得的功能权限（前端菜单 / 按钮控制依据，后端另有 @PreAuthorize 强制校验） */
    private List<String> buildPermissions(String role) {
        List<String> permissions = new ArrayList<>();
        permissions.add("project:browse");
        permissions.add("trace:query");
        if (RoleConst.STUDENT.equals(role)) {
            permissions.addAll(Arrays.asList("donation:create", "donation:cancel", "donation:list",
                    "project:favorite", "project:review", "project:share", "fund:view"));
        } else if (RoleConst.CLUB.equals(role)) {
            permissions.addAll(Arrays.asList("project:create", "project:edit", "project:material", "project:progress",
                    "donation:list:club", "fund:usage", "fund:withdrawal", "qualification:submit"));
        } else if (RoleConst.ADMIN.equals(role)) {
            permissions.addAll(Arrays.asList("project:audit", "project:offline", "user:manage", "contract:manage",
                    "log:view", "stat:view", "publicity:manage", "complaint:manage", "backup:manage"));
        } else if (RoleConst.AUDITOR.equals(role)) {
            permissions.addAll(Arrays.asList("audit:project:view", "audit:evidence:verify", "audit:fund:trace",
                    "audit:report", "audit:rule", "audit:warning"));
        }
        return permissions;
    }

    private UserDtos.UserVO toVO(SysUser user) {
        UserDtos.UserVO vo = new UserDtos.UserVO();
        vo.setUserId(user.getUserId());
        vo.setAccount(user.getAccount());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        vo.setRoleLabel(RoleConst.label(user.getRole()));
        vo.setIdentityNo(user.getIdentityNo());
        vo.setIdentityNoMasked(MaskUtil.maskIdentityNo(user.getIdentityNo()));
        vo.setPhone(user.getPhone());
        vo.setPhoneMasked(MaskUtil.maskPhone(user.getPhone()));
        vo.setEmail(user.getEmail());
        vo.setEmailMasked(MaskUtil.maskEmail(user.getEmail()));
        vo.setCollege(user.getCollege());
        vo.setMajor(user.getMajor());
        vo.setAuthStatus(identities.effectiveStatus(user));
        vo.setIdentityTestOnly(Integer.valueOf(1).equals(user.getIdentityTestOnly()));
        vo.setIdentityTestMode(identities.isTestMode());
        vo.setStatus(user.getStatus());
        vo.setAvatar(user.getAvatar());
        vo.setRemark(user.getRemark());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    /** 供其他模块使用：查询角色下的用户ID（通知相关方） */
    public List<Long> findUserIdsByRole(String role) {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, role))
                .stream().map(SysUser::getUserId).collect(Collectors.toList());
    }
}
