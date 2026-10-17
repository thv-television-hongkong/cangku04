-- =====================================================================================
-- 校园公益众筹存证溯源系统 - MySQL 8.0 数据库脚本
-- 严格对应需求文档 ER 实体：用户 / 众筹项目 / 捐赠记录 / 存证凭证 / 资金使用凭证
-- 另含支撑全部用例（收藏、评价、分享、进度、材料、提取、审核、预警、审计、投诉、
-- 日志、合约、备份、验证码、原始文件索引）所需的扩展表。
--
-- 法律约束落地：
--   * 帮扶对象个人信息脱敏展示  -> project.beneficiary_masked / beneficiary_contact_masked
--   * 链上仅哈希，原始文件在业务服务器 -> file_record 保存 file_path/file_url，evidence_record 保存 data_hash
--   * 不存原始敏感隐私          -> sys_user.id_card_hash 仅存加盐哈希，无身份证明文字段
-- =====================================================================================

-- 注意：本脚本不会删除既有数据库，避免误删生产数据；如需全新重建请手动执行 DROP DATABASE campus_charity;
-- 脚本内仅对下列业务表执行 DROP TABLE IF EXISTS 重建，执行前请确认目标库为 campus_charity。
CREATE DATABASE IF NOT EXISTS campus_charity DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE campus_charity;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================================
-- 1. 用户 (ER 实体 1)  用户ID / 账号 / 密码 / 姓名 / 角色 / 学号|社团编号 / 状态
--    角色：STUDENT 学生  CLUB 公益社团  ADMIN 平台管理员  AUDITOR 监管审计
-- =====================================================================================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    user_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    account         VARCHAR(64)  NOT NULL                COMMENT '登录账号(学号/社团编号/工号/邮箱/手机号)',
    password        VARCHAR(100) NOT NULL                COMMENT '密码(BCrypt 加密存储)',
    real_name       VARCHAR(64)  NOT NULL                COMMENT '姓名',
    role            VARCHAR(20)  NOT NULL                COMMENT '角色 STUDENT/CLUB/ADMIN/AUDITOR',
    identity_no     VARCHAR(64)           DEFAULT NULL   COMMENT '学号/社团编号/工号',
    phone           VARCHAR(20)           DEFAULT NULL   COMMENT '手机号(脱敏存展示值)',
    email           VARCHAR(128)          DEFAULT NULL   COMMENT '邮箱',
    college         VARCHAR(64)           DEFAULT NULL   COMMENT '所属学院/单位',
    major           VARCHAR(64)           DEFAULT NULL   COMMENT '专业(学生)',
    id_card_hash    VARCHAR(128)          DEFAULT NULL   COMMENT '身份证号加盐哈希(实名认证用，不存明文)',
    auth_status     TINYINT      NOT NULL DEFAULT 0      COMMENT '实名/资质认证 0未提交 1待审核 2已认证 3已驳回',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '账号状态 1正常 0冻结 2待审核',
    avatar          VARCHAR(255)          DEFAULT NULL   COMMENT '头像地址',
    last_login_time DATETIME              DEFAULT NULL   COMMENT '最后登录时间',
    last_login_ip   VARCHAR(64)           DEFAULT NULL   COMMENT '最后登录IP',
    remark          VARCHAR(255)          DEFAULT NULL   COMMENT '备注',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除 0正常 1删除',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_account (account),
    KEY idx_user_role_status (role, status),
    KEY idx_user_identity (identity_no),
    KEY idx_user_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户表(ER 实体1)';

-- =====================================================================================
-- 2. 社团资质审核（用户管理模块：社团账号需要资质审核）
-- =====================================================================================
DROP TABLE IF EXISTS club_qualification;
CREATE TABLE club_qualification (
    qualification_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '资质ID',
    user_id          BIGINT       NOT NULL                COMMENT '社团用户ID',
    club_name        VARCHAR(128) NOT NULL                COMMENT '社团名称',
    club_code        VARCHAR(64)           DEFAULT NULL   COMMENT '社团编号',
    college          VARCHAR(64)           DEFAULT NULL   COMMENT '挂靠学院',
    advisor_name     VARCHAR(64)           DEFAULT NULL   COMMENT '指导老师姓名',
    advisor_phone    VARCHAR(20)           DEFAULT NULL   COMMENT '指导老师联系方式',
    file_id          BIGINT                DEFAULT NULL   COMMENT '资质材料原始文件ID(file_record)',
    license_hash     VARCHAR(128)          DEFAULT NULL   COMMENT '资质材料文件哈希(SHA-256)',
    status           TINYINT      NOT NULL DEFAULT 1      COMMENT '1待审核 2已通过 3已驳回',
    audit_user_id    BIGINT                DEFAULT NULL   COMMENT '审核人ID',
    audit_opinion    VARCHAR(500)          DEFAULT NULL   COMMENT '审核意见',
    audit_time       DATETIME              DEFAULT NULL   COMMENT '审核时间',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (qualification_id),
    KEY idx_cq_user (user_id),
    KEY idx_cq_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '社团资质审核表';

-- =====================================================================================
-- 3. 众筹项目 (ER 实体 2)  项目ID/标题/帮扶对象/目标金额/当前筹款/起止时间/状态/社团ID/描述
-- =====================================================================================
DROP TABLE IF EXISTS project;
CREATE TABLE project (
    project_id        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '项目ID',
    project_no        VARCHAR(32)   NOT NULL                COMMENT '项目编号(业务主键)',
    title             VARCHAR(128)  NOT NULL                COMMENT '项目标题',
    category          VARCHAR(32)            DEFAULT 'OTHER' COMMENT '帮扶类别 EDUCATION/MEDICAL/POVERTY/DISASTER/OTHER',
    beneficiary       VARCHAR(128)  NOT NULL                COMMENT '帮扶对象姓名(仅社团与管理员可见，不对外展示)',
    beneficiary_masked VARCHAR(128)          DEFAULT NULL   COMMENT '帮扶对象脱敏展示名(如 李**)',
    beneficiary_contact_masked VARCHAR(64)   DEFAULT NULL   COMMENT '帮扶对象联系方式(脱敏)',
    beneficiary_desc  VARCHAR(1000)          DEFAULT NULL   COMMENT '帮扶对象情况说明(脱敏后文本)',
    target_amount     DECIMAL(14, 2) NOT NULL               COMMENT '目标金额',
    current_amount    DECIMAL(14, 2) NOT NULL DEFAULT 0.00  COMMENT '当前筹款金额',
    start_time        DATETIME      NOT NULL                COMMENT '开始时间',
    end_time          DATETIME      NOT NULL                COMMENT '截止时间',
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT草稿 PENDING待审核 REJECTED审核驳回 ONLINE已上架众筹中 OFFLINE已下架 FINISHED已完成 SETTLED已结算',
    club_id           BIGINT        NOT NULL                COMMENT '发起社团用户ID(sys_user.user_id)',
    description       TEXT                                  COMMENT '项目描述',
    funds_plan        TEXT                                  COMMENT '资金使用计划',
    cover_image       VARCHAR(255)           DEFAULT NULL   COMMENT '封面图地址',
    donor_count       INT           NOT NULL DEFAULT 0      COMMENT '捐赠人数',
    view_count        INT           NOT NULL DEFAULT 0      COMMENT '浏览量',
    favorite_count    INT           NOT NULL DEFAULT 0      COMMENT '收藏量',
    share_count       INT           NOT NULL DEFAULT 0      COMMENT '分享量',
    progress_percent  INT           NOT NULL DEFAULT 0      COMMENT '项目执行进度(0-100)',
    data_hash         VARCHAR(128)           DEFAULT NULL   COMMENT '项目摘要哈希(SHA-256，上链内容)',
    tx_id             VARCHAR(128)           DEFAULT NULL   COMMENT '最新存证交易ID',
    block_height      BIGINT                 DEFAULT NULL   COMMENT '最新存证区块高度',
    evidence_id       BIGINT                 DEFAULT NULL   COMMENT '最新存证凭证ID',
    audit_user_id     BIGINT                 DEFAULT NULL   COMMENT '审核管理员ID',
    audit_opinion     VARCHAR(500)           DEFAULT NULL   COMMENT '最近审核意见',
    audit_time        DATETIME               DEFAULT NULL   COMMENT '最近审核时间',
    offline_reason    VARCHAR(500)           DEFAULT NULL   COMMENT '下架原因',
    finish_time       DATETIME               DEFAULT NULL   COMMENT '结项时间',
    deleted           TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (project_id),
    UNIQUE KEY uk_project_no (project_no),
    KEY idx_project_club (club_id),
    KEY idx_project_status (status),
    KEY idx_project_status_time (status, end_time),
    KEY idx_project_tx (tx_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '众筹项目表(ER 实体2)';

-- =====================================================================================
-- 4. 捐赠记录 (ER 实体 3)  捐赠ID/项目ID/捐赠人ID/金额/时间/交易ID
-- =====================================================================================
DROP TABLE IF EXISTS donation;
CREATE TABLE donation (
    donation_id     BIGINT         NOT NULL AUTO_INCREMENT COMMENT '捐赠ID',
    donation_no     VARCHAR(32)    NOT NULL                COMMENT '捐赠单号',
    project_id      BIGINT         NOT NULL                COMMENT '项目ID',
    donor_id        BIGINT         NOT NULL                COMMENT '捐赠人ID',
    amount          DECIMAL(14, 2) NOT NULL                COMMENT '捐赠金额',
    donate_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '捐赠时间',
    pay_channel     VARCHAR(20)             DEFAULT 'CAMPUS_CARD' COMMENT '支付渠道 CAMPUS_CARD校园一卡通/ALIPAY/WECHAT/OFFLINE',
    status          VARCHAR(16)    NOT NULL DEFAULT 'PAID'  COMMENT 'PAID已支付 CANCELLED已取消 CONFIRMED已入账',
    cancel_deadline DATETIME                DEFAULT NULL    COMMENT '可取消截止时间(限定时间内取消捐赠)',
    cancel_time     DATETIME                DEFAULT NULL    COMMENT '取消时间',
    cancel_reason   VARCHAR(255)            DEFAULT NULL    COMMENT '取消原因',
    anonymous       TINYINT        NOT NULL DEFAULT 0       COMMENT '是否匿名捐赠',
    remark          VARCHAR(255)            DEFAULT NULL    COMMENT '捐赠留言',
    data_hash       VARCHAR(128)            DEFAULT NULL    COMMENT '捐赠摘要哈希(SHA-256，上链内容)',
    tx_id           VARCHAR(128)            DEFAULT NULL    COMMENT '存证交易ID',
    block_height    BIGINT                  DEFAULT NULL    COMMENT '存证区块高度',
    evidence_id     BIGINT                  DEFAULT NULL    COMMENT '存证凭证ID',
    deleted         TINYINT        NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (donation_id),
    UNIQUE KEY uk_donation_no (donation_no),
    KEY idx_donation_project (project_id, status),
    KEY idx_donation_donor (donor_id),
    KEY idx_donation_time (donate_time),
    KEY idx_donation_tx (tx_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '捐赠记录表(ER 实体3)';

-- =====================================================================================
-- 5. 存证凭证 (ER 实体 4)  凭证ID/交易ID/区块高度/上链时间/存证哈希/关联业务ID
--    每一条业务（项目 / 捐赠 / 资金凭证 / 帮扶材料 / 资金提取 / 项目进度）对应一条凭证
-- =====================================================================================
DROP TABLE IF EXISTS evidence_record;
CREATE TABLE evidence_record (
    evidence_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '凭证ID',
    evidence_no    VARCHAR(40)  NOT NULL                COMMENT '凭证编号',
    tx_id          VARCHAR(128) NOT NULL                COMMENT 'Fabric 交易ID',
    block_height   BIGINT                DEFAULT NULL   COMMENT '区块高度',
    chain_time     DATETIME              DEFAULT NULL   COMMENT '上链时间(链上交易时间戳)',
    data_hash      VARCHAR(128) NOT NULL                COMMENT '存证数据哈希(SHA-256，链上唯一内容)',
    biz_type       VARCHAR(32)  NOT NULL                COMMENT '业务类型 PROJECT/DONATION/FUND_USAGE/MATERIAL/WITHDRAWAL/PROJECT_PROGRESS',
    biz_id         VARCHAR(64)  NOT NULL                COMMENT '关联业务ID',
    project_id     BIGINT                DEFAULT NULL   COMMENT '关联项目ID(便于按项目溯源)',
    file_id        BIGINT                DEFAULT NULL   COMMENT '链下原始文件ID(file_record)，可为空',
    channel_name   VARCHAR(64)           DEFAULT NULL   COMMENT '通道名称',
    chaincode_name VARCHAR(64)           DEFAULT NULL   COMMENT '链码名称',
    function_name  VARCHAR(64)           DEFAULT NULL   COMMENT '调用的链码方法',
    operator_id    BIGINT                DEFAULT NULL   COMMENT '操作人ID',
    operator_name  VARCHAR(64)           DEFAULT NULL   COMMENT '操作人姓名',
    status         VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED/PENDING',
    payload        TEXT                                 COMMENT '上链业务摘要(JSON，仅哈希与非敏感字段)',
    error_msg      VARCHAR(500)          DEFAULT NULL   COMMENT '失败原因',
    chain_source   VARCHAR(16)  NOT NULL DEFAULT 'FABRIC' COMMENT 'FABRIC真实链 / MOCK模拟链',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (evidence_id),
    UNIQUE KEY uk_evidence_tx (tx_id),
    KEY idx_evidence_biz (biz_type, biz_id),
    KEY idx_evidence_project (project_id),
    KEY idx_evidence_block (block_height),
    KEY idx_evidence_hash (data_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '存证凭证表(ER 实体4)';

-- =====================================================================================
-- 6. 资金使用凭证 (ER 实体 5)  凭证编号/项目ID/上传社团ID/资金使用说明/文件哈希/上传时间/审核状态
-- =====================================================================================
DROP TABLE IF EXISTS fund_usage;
CREATE TABLE fund_usage (
    usage_id      BIGINT         NOT NULL AUTO_INCREMENT COMMENT '凭证编号',
    usage_no      VARCHAR(32)    NOT NULL                COMMENT '凭证业务编号',
    project_id    BIGINT         NOT NULL                COMMENT '项目ID',
    club_id       BIGINT         NOT NULL                COMMENT '上传社团ID',
    usage_desc    VARCHAR(1000)  NOT NULL                COMMENT '资金使用说明',
    amount        DECIMAL(14, 2) NOT NULL                COMMENT '本次资金使用金额',
    use_time      DATETIME                DEFAULT NULL   COMMENT '资金实际使用时间',
    file_id       BIGINT                  DEFAULT NULL   COMMENT '链下原始文件ID(发票/收据)',
    file_name     VARCHAR(255)            DEFAULT NULL   COMMENT '原始文件名',
    file_url      VARCHAR(255)            DEFAULT NULL   COMMENT '原始文件访问地址(业务服务器)',
    file_hash     VARCHAR(128)            DEFAULT NULL   COMMENT '文件哈希(SHA-256，上链内容)',
    upload_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    audit_status  TINYINT        NOT NULL DEFAULT 0      COMMENT '审核状态 0待审核 1已通过 2已驳回',
    audit_user_id BIGINT                  DEFAULT NULL   COMMENT '审核人ID',
    audit_opinion VARCHAR(500)            DEFAULT NULL   COMMENT '审核意见',
    audit_time    DATETIME                DEFAULT NULL   COMMENT '审核时间',
    data_hash     VARCHAR(128)            DEFAULT NULL   COMMENT '资金凭证存证哈希',
    tx_id         VARCHAR(128)            DEFAULT NULL   COMMENT '存证交易ID',
    block_height  BIGINT                  DEFAULT NULL   COMMENT '存证区块高度',
    evidence_id   BIGINT                  DEFAULT NULL   COMMENT '存证凭证ID',
    deleted       TINYINT        NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (usage_id),
    UNIQUE KEY uk_fund_usage_no (usage_no),
    KEY idx_fu_project (project_id),
    KEY idx_fu_club (club_id),
    KEY idx_fu_audit (audit_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '资金使用凭证表(ER 实体5)';

-- =====================================================================================
-- 7. 项目证明材料（受助者用例：提交项目证明材料 -> 材料哈希上链）
-- =====================================================================================
DROP TABLE IF EXISTS project_material;
CREATE TABLE project_material (
    material_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '材料ID',
    project_id    BIGINT       NOT NULL                COMMENT '项目ID',
    club_id       BIGINT       NOT NULL                COMMENT '上传社团ID',
    material_type VARCHAR(32)  NOT NULL DEFAULT 'PROOF' COMMENT 'PROJECT_PLAN项目计划书 PROOF证明材料 BENEFICIARY帮扶材料 OTHER其他',
    material_name VARCHAR(128) NOT NULL                COMMENT '材料名称',
    remark        VARCHAR(500)          DEFAULT NULL   COMMENT '材料说明',
    file_id       BIGINT                DEFAULT NULL   COMMENT '链下原始文件ID',
    file_name     VARCHAR(255)          DEFAULT NULL   COMMENT '原始文件名',
    file_url      VARCHAR(255)          DEFAULT NULL   COMMENT '原始文件地址',
    file_hash     VARCHAR(128) NOT NULL                COMMENT '文件哈希(SHA-256)',
    file_size     BIGINT                DEFAULT NULL   COMMENT '文件大小(字节)',
    on_chain      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已上链 0未上链 1已上链',
    upload_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    data_hash     VARCHAR(128)          DEFAULT NULL   COMMENT '存证哈希',
    tx_id         VARCHAR(128)          DEFAULT NULL   COMMENT '交易ID',
    block_height  BIGINT                DEFAULT NULL   COMMENT '区块高度',
    evidence_id   BIGINT                DEFAULT NULL   COMMENT '存证凭证ID',
    deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (material_id),
    KEY idx_pm_project (project_id),
    KEY idx_pm_type (material_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '项目/帮扶证明材料表';

-- =====================================================================================
-- 8. 项目进度（受助者用例：编辑项目 include 更新项目进度）
-- =====================================================================================
DROP TABLE IF EXISTS project_progress;
CREATE TABLE project_progress (
    progress_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '进度ID',
    project_id       BIGINT       NOT NULL                COMMENT '项目ID',
    title            VARCHAR(128) NOT NULL                COMMENT '进度标题',
    content          VARCHAR(1000)         DEFAULT NULL   COMMENT '进度内容',
    progress_percent INT          NOT NULL DEFAULT 0      COMMENT '完成百分比',
    file_id          BIGINT                DEFAULT NULL   COMMENT '进度附件原始文件ID',
    file_hash        VARCHAR(128)          DEFAULT NULL   COMMENT '附件哈希',
    create_user_id   BIGINT       NOT NULL                COMMENT '提交人ID',
    data_hash        VARCHAR(128)          DEFAULT NULL   COMMENT '进度存证哈希',
    tx_id            VARCHAR(128)          DEFAULT NULL   COMMENT '交易ID',
    block_height     BIGINT                DEFAULT NULL   COMMENT '区块高度',
    evidence_id      BIGINT                DEFAULT NULL   COMMENT '存证凭证ID',
    deleted          TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (progress_id),
    KEY idx_pp_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '项目进度表';

-- =====================================================================================
-- 9. 资金提取申请（受助者用例：申请资金提取 -> 提取记录哈希上链）
-- =====================================================================================
DROP TABLE IF EXISTS fund_withdrawal;
CREATE TABLE fund_withdrawal (
    withdrawal_id BIGINT         NOT NULL AUTO_INCREMENT COMMENT '提取申请ID',
    withdrawal_no VARCHAR(32)    NOT NULL                COMMENT '提取单号',
    project_id    BIGINT         NOT NULL                COMMENT '项目ID',
    club_id       BIGINT         NOT NULL                COMMENT '申请社团ID',
    amount        DECIMAL(14, 2) NOT NULL                COMMENT '申请提取金额',
    reason        VARCHAR(1000)  NOT NULL                COMMENT '提取用途说明',
    stage         VARCHAR(64)             DEFAULT NULL   COMMENT '阶段说明(阶段性目标)',
    apply_time    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    status        TINYINT        NOT NULL DEFAULT 0      COMMENT '0待审核 1已通过 2已驳回 3已发放',
    audit_user_id BIGINT                  DEFAULT NULL   COMMENT '审核人ID',
    audit_opinion VARCHAR(500)            DEFAULT NULL   COMMENT '审核意见',
    audit_time    DATETIME                DEFAULT NULL   COMMENT '审核时间',
    pay_time      DATETIME                DEFAULT NULL   COMMENT '发放时间',
    data_hash     VARCHAR(128)            DEFAULT NULL   COMMENT '提取记录存证哈希',
    tx_id         VARCHAR(128)            DEFAULT NULL   COMMENT '交易ID',
    block_height  BIGINT                  DEFAULT NULL   COMMENT '区块高度',
    evidence_id   BIGINT                  DEFAULT NULL   COMMENT '存证凭证ID',
    deleted       TINYINT        NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (withdrawal_id),
    UNIQUE KEY uk_withdrawal_no (withdrawal_no),
    KEY idx_fw_project (project_id),
    KEY idx_fw_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '资金提取申请表';

-- =====================================================================================
-- 10. 项目收藏（捐赠者用例：查看项目详情 include 收藏项目）
-- =====================================================================================
DROP TABLE IF EXISTS project_favorite;
CREATE TABLE project_favorite (
    favorite_id BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
    project_id  BIGINT   NOT NULL                COMMENT '项目ID',
    user_id     BIGINT   NOT NULL                COMMENT '用户ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (favorite_id),
    UNIQUE KEY uk_favorite (project_id, user_id),
    KEY idx_favorite_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '项目收藏表';

-- =====================================================================================
-- 11. 项目评价（捐赠者用例：评价项目）
-- =====================================================================================
DROP TABLE IF EXISTS project_review;
CREATE TABLE project_review (
    review_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    project_id    BIGINT       NOT NULL                COMMENT '项目ID',
    user_id       BIGINT       NOT NULL                COMMENT '评价人ID',
    donation_id   BIGINT                DEFAULT NULL   COMMENT '关联捐赠ID(捐赠后可评价)',
    rating        TINYINT      NOT NULL DEFAULT 5      COMMENT '评分 1-5',
    content       VARCHAR(1000)         DEFAULT NULL   COMMENT '评价内容',
    anonymous     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否匿名',
    status        TINYINT      NOT NULL DEFAULT 1      COMMENT '1显示 0隐藏',
    reply_content VARCHAR(1000)         DEFAULT NULL   COMMENT '社团/管理员回复',
    reply_time    DATETIME              DEFAULT NULL   COMMENT '回复时间',
    deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (review_id),
    KEY idx_review_project (project_id),
    KEY idx_review_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '项目评价表(可拓展功能)';

-- =====================================================================================
-- 12. 项目分享记录（捐赠者用例：查看资金流向 extends 分享项目）
-- =====================================================================================
DROP TABLE IF EXISTS project_share_log;
CREATE TABLE project_share_log (
    share_id    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分享ID',
    project_id  BIGINT      NOT NULL                COMMENT '项目ID',
    user_id     BIGINT               DEFAULT NULL   COMMENT '分享人ID',
    channel     VARCHAR(32)          DEFAULT 'LINK' COMMENT '分享渠道 LINK/QRCODE/WECHAT/QQ',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分享时间',
    PRIMARY KEY (share_id),
    KEY idx_share_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '项目分享记录表';

-- =====================================================================================
-- 13. 项目审核记录（管理员用例：审核众筹项目 include 项目合规审查 / 查看审核意见）
-- =====================================================================================
DROP TABLE IF EXISTS project_audit;
CREATE TABLE project_audit (
    audit_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    project_id       BIGINT       NOT NULL                COMMENT '项目ID',
    auditor_id       BIGINT                DEFAULT NULL   COMMENT '审核人ID',
    auditor_name     VARCHAR(64)           DEFAULT NULL   COMMENT '审核人姓名',
    audit_type       VARCHAR(32)  NOT NULL DEFAULT 'SUBMIT' COMMENT 'SUBMIT提交审核 APPROVE通过 REJECT驳回 OFFLINE下架 COMPLAINT投诉处置 FINISH结项',
    result           VARCHAR(20)           DEFAULT NULL   COMMENT 'PASS/REJECT/OFFLINE',
    opinion          VARCHAR(1000)         DEFAULT NULL   COMMENT '审核意见',
    compliance_items TEXT                                 COMMENT '合规审查项 JSON(真实性/合规性/材料完整性/预算合理性/帮扶对象真实性)',
    audit_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
    notify_status    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已通知社团 0未通知 1已通知',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (audit_id),
    KEY idx_pa_project (project_id),
    KEY idx_pa_type (audit_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '项目审核记录表';

-- =====================================================================================
-- 14. 投诉与纠纷（管理员用例：处理投诉与纠纷 extends 通知相关方）
-- =====================================================================================
DROP TABLE IF EXISTS complaint;
CREATE TABLE complaint (
    complaint_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '投诉ID',
    complaint_no   VARCHAR(32)  NOT NULL                COMMENT '投诉单号',
    project_id     BIGINT                DEFAULT NULL   COMMENT '被投诉项目ID',
    user_id        BIGINT       NOT NULL                COMMENT '投诉人ID',
    complaint_type VARCHAR(32)  NOT NULL DEFAULT 'OTHER' COMMENT 'FUND_USE资金使用/FAKE_PROJECT虚假项目/PRIVACY隐私/DELAY进度滞后/OTHER其他',
    title          VARCHAR(128) NOT NULL                COMMENT '投诉标题',
    content        VARCHAR(2000) NOT NULL               COMMENT '投诉内容',
    evidence_file_id BIGINT              DEFAULT NULL   COMMENT '投诉证据原始文件ID',
    status         TINYINT      NOT NULL DEFAULT 0      COMMENT '0待处理 1处理中 2已解决 3已驳回',
    handler_id     BIGINT                DEFAULT NULL   COMMENT '处理人ID',
    handle_result  VARCHAR(1000)         DEFAULT NULL   COMMENT '处理结果',
    handle_time    DATETIME              DEFAULT NULL   COMMENT '处理时间',
    notify_status  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已通知相关方 0未通知 1已通知',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投诉时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (complaint_id),
    UNIQUE KEY uk_complaint_no (complaint_no),
    KEY idx_complaint_status (status),
    KEY idx_complaint_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '投诉纠纷表';

-- =====================================================================================
-- 15. 站内通知（extends 通知相关方 / 审核结果通知）
-- =====================================================================================
DROP TABLE IF EXISTS notification;
CREATE TABLE notification (
    notice_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    user_id     BIGINT       NOT NULL                COMMENT '接收人ID',
    title       VARCHAR(128) NOT NULL                COMMENT '标题',
    content     VARCHAR(1000)         DEFAULT NULL   COMMENT '内容',
    notice_type VARCHAR(32)  NOT NULL DEFAULT 'SYSTEM' COMMENT 'PROJECT_AUDIT项目审核/FUND提取审核/COMPLAINT投诉/WARNING预警/SYSTEM系统',
    biz_id      VARCHAR(64)           DEFAULT NULL   COMMENT '关联业务ID',
    read_status TINYINT      NOT NULL DEFAULT 0      COMMENT '0未读 1已读',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (notice_id),
    KEY idx_notice_user (user_id, read_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '站内通知表';

-- =====================================================================================
-- 16. 系统操作日志（管理员用例：查看系统日志 include 操作审计追踪）
-- =====================================================================================
DROP TABLE IF EXISTS sys_operation_log;
CREATE TABLE sys_operation_log (
    log_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    user_id        BIGINT                DEFAULT NULL   COMMENT '操作人ID',
    account        VARCHAR(64)           DEFAULT NULL   COMMENT '操作人账号',
    real_name      VARCHAR(64)           DEFAULT NULL   COMMENT '操作人姓名',
    role           VARCHAR(20)           DEFAULT NULL   COMMENT '操作人角色',
    module         VARCHAR(64)           DEFAULT NULL   COMMENT '业务模块',
    operation      VARCHAR(128)          DEFAULT NULL   COMMENT '操作描述',
    method         VARCHAR(255)          DEFAULT NULL   COMMENT 'Java 方法',
    request_uri    VARCHAR(255)          DEFAULT NULL   COMMENT '请求地址',
    request_method VARCHAR(10)           DEFAULT NULL   COMMENT '请求方式',
    request_params TEXT                                 COMMENT '请求参数(已脱敏)',
    ip             VARCHAR(64)           DEFAULT NULL   COMMENT '客户端IP',
    user_agent     VARCHAR(255)          DEFAULT NULL   COMMENT '浏览器UA',
    result_status  VARCHAR(16)           DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAIL',
    error_msg      VARCHAR(1000)         DEFAULT NULL   COMMENT '异常信息',
    duration_ms    BIGINT                DEFAULT NULL   COMMENT '耗时(毫秒)',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (log_id),
    KEY idx_log_user (user_id),
    KEY idx_log_time (create_time),
    KEY idx_log_module (module)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统操作日志表(操作审计追踪)';

-- =====================================================================================
-- 17. 审核 / 预警规则（监管方用例：管理审核规则 extends 设置预警规则）
-- =====================================================================================
DROP TABLE IF EXISTS audit_rule;
CREATE TABLE audit_rule (
    rule_id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '规则ID',
    rule_name       VARCHAR(128)  NOT NULL                COMMENT '规则名称',
    rule_type       VARCHAR(32)   NOT NULL                COMMENT 'SINGLE_AMOUNT单笔金额/FREQUENCY频次/PROJECT_TARGET项目目标金额/DURATION项目周期/DAILY_TOTAL单日累计/PROJECT_PROGRESS进度异常/OTHER',
    biz_type        VARCHAR(32)   NOT NULL DEFAULT 'DONATION' COMMENT '适用业务 DONATION/PROJECT/FUND_USAGE/WITHDRAWAL',
    comparison      VARCHAR(4)    NOT NULL DEFAULT '>'     COMMENT '比较符 > >= < <= = !=',
    threshold_value DECIMAL(14, 2) NOT NULL               COMMENT '阈值',
    threshold_unit  VARCHAR(16)            DEFAULT 'YUAN' COMMENT '单位 YUAN元/COUNT次数/DAY天/PERCENT百分比',
    warning_level   VARCHAR(16)   NOT NULL DEFAULT 'MID'   COMMENT '预警级别 LOW低 MID中 HIGH高',
    enabled         TINYINT       NOT NULL DEFAULT 1       COMMENT '是否启用',
    description     VARCHAR(500)           DEFAULT NULL   COMMENT '规则说明',
    hit_count       INT           NOT NULL DEFAULT 0       COMMENT '命中次数',
    create_user_id  BIGINT                 DEFAULT NULL   COMMENT '创建人ID',
    deleted         TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (rule_id),
    KEY idx_rule_type (rule_type, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '审核/预警规则表';

-- =====================================================================================
-- 18. 异常交易预警记录
-- =====================================================================================
DROP TABLE IF EXISTS warning_record;
CREATE TABLE warning_record (
    warning_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '预警ID',
    rule_id         BIGINT                DEFAULT NULL   COMMENT '命中规则ID',
    rule_name       VARCHAR(128)          DEFAULT NULL   COMMENT '规则名称',
    biz_type        VARCHAR(32)  NOT NULL                COMMENT '业务类型',
    biz_id          VARCHAR(64)           DEFAULT NULL   COMMENT '业务ID',
    project_id      BIGINT                DEFAULT NULL   COMMENT '项目ID',
    user_id         BIGINT                DEFAULT NULL   COMMENT '触发用户ID',
    warning_level   VARCHAR(16)  NOT NULL DEFAULT 'MID'   COMMENT '预警级别',
    warning_content VARCHAR(1000) NOT NULL               COMMENT '预警内容',
    warning_value   DECIMAL(14, 2)        DEFAULT NULL   COMMENT '触发值',
    handle_status   TINYINT      NOT NULL DEFAULT 0       COMMENT '0未处理 1已处理 2已忽略',
    handler_id      BIGINT                DEFAULT NULL   COMMENT '处理人ID',
    handle_opinion  VARCHAR(500)          DEFAULT NULL   COMMENT '处理意见',
    handle_time     DATETIME              DEFAULT NULL   COMMENT '处理时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预警时间',
    PRIMARY KEY (warning_id),
    KEY idx_warning_handle (handle_status),
    KEY idx_warning_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '异常交易预警记录表';

-- =====================================================================================
-- 19. 审计报告（监管方用例：生成审计报告 include 导出存证数据）
-- =====================================================================================
DROP TABLE IF EXISTS audit_report;
CREATE TABLE audit_report (
    report_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '报告ID',
    report_no            VARCHAR(32)  NOT NULL                COMMENT '报告编号',
    title                VARCHAR(200) NOT NULL                COMMENT '报告标题',
    report_type          VARCHAR(32)  NOT NULL DEFAULT 'COMPREHENSIVE' COMMENT 'PROJECT项目审计/FUND资金审计/EVIDENCE存证审计/COMPREHENSIVE综合审计',
    project_id           BIGINT                DEFAULT NULL   COMMENT '项目ID(项目审计时)',
    start_time           DATETIME              DEFAULT NULL   COMMENT '统计开始时间',
    end_time             DATETIME              DEFAULT NULL   COMMENT '统计结束时间',
    summary              TEXT                                  COMMENT '报告摘要',
    total_amount         DECIMAL(16, 2)        DEFAULT 0.00   COMMENT '涉及金额',
    donation_count       INT                    DEFAULT 0      COMMENT '捐赠笔数',
    evidence_count       INT                    DEFAULT 0      COMMENT '存证条数',
    warning_count        INT                    DEFAULT 0      COMMENT '预警条数',
    verify_result        VARCHAR(32)            DEFAULT NULL   COMMENT '哈希核验结论 ALL_MATCH 全部一致 / PARTIAL_MISMATCH 存在不一致',
    file_id              BIGINT                DEFAULT NULL   COMMENT '报告文件ID',
    file_url             VARCHAR(255)          DEFAULT NULL   COMMENT '报告文件地址',
    file_hash            VARCHAR(128)          DEFAULT NULL   COMMENT '报告文件哈希(可上链)',
    tx_id                VARCHAR(128)          DEFAULT NULL   COMMENT '报告存证交易ID',
    block_height         BIGINT                DEFAULT NULL   COMMENT '区块高度',
    generate_user_id     BIGINT                DEFAULT NULL   COMMENT '生成人ID',
    generate_user_name   VARCHAR(64)           DEFAULT NULL   COMMENT '生成人姓名',
    generate_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (report_id),
    UNIQUE KEY uk_report_no (report_no),
    KEY idx_report_project (project_id),
    KEY idx_report_time (generate_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '审计报告表';

-- =====================================================================================
-- 20. 原始文件索引（链下存储，与链上哈希一一对应）
-- =====================================================================================
DROP TABLE IF EXISTS file_record;
CREATE TABLE file_record (
    file_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    file_no      VARCHAR(40)  NOT NULL                COMMENT '文件编号',
    original_name VARCHAR(255) NOT NULL               COMMENT '原始文件名',
    stored_name  VARCHAR(255) NOT NULL                COMMENT '服务器存储文件名',
    file_path    VARCHAR(500) NOT NULL                COMMENT '业务服务器存储绝对路径',
    file_url     VARCHAR(255)          DEFAULT NULL   COMMENT '对外访问地址',
    file_hash    VARCHAR(128) NOT NULL                COMMENT '文件SHA-256哈希(上链内容)',
    file_size    BIGINT                DEFAULT 0      COMMENT '文件大小(字节)',
    content_type VARCHAR(128)          DEFAULT NULL   COMMENT 'MIME类型',
    biz_type     VARCHAR(32)  NOT NULL                COMMENT '业务类型 PROJECT_MATERIAL/FUND_USAGE/PROJECT_COVER/QUALIFICATION/AUDIT_REPORT/OTHER',
    biz_id       VARCHAR(64)           DEFAULT NULL   COMMENT '业务ID',
    uploader_id  BIGINT                DEFAULT NULL   COMMENT '上传人ID',
    on_chain     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已上链存证',
    tx_id        VARCHAR(128)          DEFAULT NULL   COMMENT '上链交易ID',
    block_height BIGINT                DEFAULT NULL   COMMENT '区块高度',
    hash_verified TINYINT     NOT NULL DEFAULT 0      COMMENT '是否已通过哈希核验 0未核验 1一致 2不一致',
    verify_time  DATETIME              DEFAULT NULL   COMMENT '最近核验时间',
    deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (file_id),
    UNIQUE KEY uk_file_no (file_no),
    KEY idx_file_biz (biz_type, biz_id),
    KEY idx_file_hash (file_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '原始文件索引表(文件存业务服务器，仅哈希上链)';

-- =====================================================================================
-- 21. 验证码（注册 / 找回密码 / 敏感操作）
-- =====================================================================================
DROP TABLE IF EXISTS verify_code;
CREATE TABLE verify_code (
    code_id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '验证码ID',
    target      VARCHAR(128) NOT NULL               COMMENT '手机号或邮箱',
    code        VARCHAR(16)  NOT NULL               COMMENT '验证码',
    biz_type    VARCHAR(32)  NOT NULL DEFAULT 'RESET_PASSWORD' COMMENT 'REGISTER注册 RESET_PASSWORD找回密码 LOGIN登录',
    expire_time DATETIME     NOT NULL               COMMENT '过期时间',
    used        TINYINT      NOT NULL DEFAULT 0     COMMENT '0未使用 1已使用',
    send_ip     VARCHAR(64)           DEFAULT NULL  COMMENT '请求IP',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (code_id),
    KEY idx_code_target (target, biz_type, used)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '验证码表';

-- =====================================================================================
-- 22. 智能合约管理（管理员用例：管理智能合约 extends 部署合约升级）
-- =====================================================================================
DROP TABLE IF EXISTS contract_record;
CREATE TABLE contract_record (
    contract_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    chaincode_name   VARCHAR(64)  NOT NULL                COMMENT '链码名称',
    chaincode_version VARCHAR(32) NOT NULL                COMMENT '链码版本',
    sequence_no      BIGINT                DEFAULT NULL   COMMENT '链码序列号',
    channel_name     VARCHAR(64)  NOT NULL DEFAULT 'charitychannel' COMMENT '通道名称',
    operation_type   VARCHAR(16)  NOT NULL DEFAULT 'DEPLOY' COMMENT 'DEPLOY部署/UPGRADE升级',
    package_id       VARCHAR(255)          DEFAULT NULL   COMMENT '链码包ID',
    endorsement_policy VARCHAR(255)        DEFAULT NULL   COMMENT '背书策略',
    tx_id            VARCHAR(128)          DEFAULT NULL   COMMENT '部署交易ID',
    block_height     BIGINT                DEFAULT NULL   COMMENT '区块高度',
    status           VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
    error_msg        VARCHAR(500)          DEFAULT NULL   COMMENT '失败原因',
    operator_id      BIGINT                DEFAULT NULL   COMMENT '操作人ID',
    operator_name    VARCHAR(64)           DEFAULT NULL   COMMENT '操作人姓名',
    description      VARCHAR(500)          DEFAULT NULL   COMMENT '说明',
    operation_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (contract_id),
    KEY idx_contract_name (chaincode_name, chaincode_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '智能合约部署/升级记录表';

-- =====================================================================================
-- 23. 公益信息公示（管理员用例：项目校内公示）
-- =====================================================================================
DROP TABLE IF EXISTS publicity;
CREATE TABLE publicity (
    publicity_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '公示ID',
    title          VARCHAR(200) NOT NULL                COMMENT '公示标题',
    content        TEXT                                  COMMENT '公示内容',
    project_id     BIGINT                DEFAULT NULL   COMMENT '关联项目ID',
    publicity_type VARCHAR(32)  NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT项目公示/ANNOUNCEMENT公告/FUND资金公示',
    status         TINYINT      NOT NULL DEFAULT 1      COMMENT '1公示中 0已撤回',
    view_count     INT          NOT NULL DEFAULT 0      COMMENT '浏览量',
    publish_user_id BIGINT                DEFAULT NULL  COMMENT '发布人ID',
    publish_user_name VARCHAR(64)         DEFAULT NULL  COMMENT '发布人姓名',
    publish_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (publicity_id),
    KEY idx_publicity_status (status),
    KEY idx_publicity_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '公益信息公示表';

-- =====================================================================================
-- 24. 数据库备份记录（可靠性：MySQL 业务数据定期备份）
-- =====================================================================================
DROP TABLE IF EXISTS sys_backup_record;
CREATE TABLE sys_backup_record (
    backup_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '备份ID',
    backup_name   VARCHAR(200) NOT NULL                COMMENT '备份文件名',
    backup_path   VARCHAR(500) NOT NULL                COMMENT '备份文件路径',
    backup_size   BIGINT                DEFAULT 0      COMMENT '备份文件大小(字节)',
    backup_type   VARCHAR(16)  NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO自动/MANUAL手动',
    status        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
    error_msg     VARCHAR(1000)         DEFAULT NULL   COMMENT '失败原因',
    duration_ms   BIGINT                DEFAULT NULL   COMMENT '耗时(毫秒)',
    trigger_user_id BIGINT              DEFAULT NULL   COMMENT '触发人ID',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间',
    PRIMARY KEY (backup_id),
    KEY idx_backup_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '数据库备份记录表';

-- =====================================================================================
-- 25. 链上存证汇总快照（业务库损坏时的链上凭证恢复辅助）
-- =====================================================================================
DROP TABLE IF EXISTS chain_evidence_snapshot;
CREATE TABLE chain_evidence_snapshot (
    snapshot_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '快照ID',
    tx_id         VARCHAR(128) NOT NULL                COMMENT '交易ID',
    block_height  BIGINT                DEFAULT NULL   COMMENT '区块高度',
    biz_type      VARCHAR(32)  NOT NULL                COMMENT '业务类型',
    biz_id        VARCHAR(64)  NOT NULL                COMMENT '业务ID',
    project_id    BIGINT                DEFAULT NULL   COMMENT '项目ID',
    data_hash     VARCHAR(128) NOT NULL                COMMENT '存证哈希',
    chain_time    DATETIME              DEFAULT NULL   COMMENT '链上时间',
    raw_payload   TEXT                                  COMMENT '链上返回的原始 JSON(仅哈希与非敏感字段)',
    sync_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '同步时间',
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uk_snapshot_tx (tx_id),
    KEY idx_snapshot_biz (biz_type, biz_id),
    KEY idx_snapshot_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '链上存证汇总快照表';

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================================
-- ER 关系说明（外键以逻辑外键实现，便于分库与链上链下解耦）：
--   sys_user 1 ── n project            (project.club_id           -> sys_user.user_id)
--   sys_user 1 ── n donation           (donation.donor_id         -> sys_user.user_id)
--   sys_user 1 ── n club_qualification (club_qualification.user_id-> sys_user.user_id)
--   project  1 ── n donation           (donation.project_id       -> project.project_id)
--   project  1 ── n fund_usage         (fund_usage.project_id     -> project.project_id)
--   project  1 ── n project_material   (project_material.project_id -> project.project_id)
--   project  1 ── n project_progress   (project_progress.project_id -> project.project_id)
--   project  1 ── n fund_withdrawal    (fund_withdrawal.project_id -> project.project_id)
--   donation 1 ── 1 evidence_record    (evidence_record.biz_type='DONATION' AND biz_id=donation_id)
--   project  1 ── n evidence_record    (evidence_record.project_id -> project.project_id)
--   fund_usage 1 ─ 1 evidence_record   (biz_type='FUND_USAGE')
--   file_record 1 ─ 0..1 evidence_record (evidence_record.file_id -> file_record.file_id)
-- =====================================================================================
