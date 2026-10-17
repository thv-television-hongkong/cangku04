-- =====================================================================================
-- 校园公益众筹存证溯源系统 - 视图脚本
-- 用途：资金流向追踪、项目统计报表、链上链下对照，支撑"溯源查询 / 数据统计 / 资金链追踪"
-- 执行：mysql -uroot -p campus_charity < database/views.sql
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 1. 项目资金流向：筹款 / 提取 / 使用 / 结余（监管方"资金链追踪"数据来源）
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_project_fund_flow AS
SELECT p.project_id,
       p.project_no,
       p.title,
       p.status                                   AS project_status,
       p.club_id,
       c.real_name                                AS club_name,
       p.target_amount,
       p.current_amount,
       IFNULL(w.withdraw_total, 0)                AS withdraw_total,
       IFNULL(fu.usage_total, 0)                  AS usage_total,
       p.current_amount - IFNULL(w.withdraw_total, 0) AS balance_amount,
       p.current_amount - IFNULL(fu.usage_total, 0)   AS usage_balance,
       ROUND(p.current_amount / NULLIF(p.target_amount, 0) * 100, 2) AS funding_percent,
       p.donor_count,
       p.create_time
FROM project p
         LEFT JOIN sys_user c ON c.user_id = p.club_id
         LEFT JOIN (SELECT project_id, SUM(amount) AS withdraw_total
                    FROM fund_withdrawal
                    WHERE status IN (1, 3) AND deleted = 0
                    GROUP BY project_id) w ON w.project_id = p.project_id
         LEFT JOIN (SELECT project_id, SUM(amount) AS usage_total
                    FROM fund_usage
                    WHERE deleted = 0 AND audit_status=1
                    GROUP BY project_id) fu ON fu.project_id = p.project_id
WHERE p.deleted = 0;

-- -------------------------------------------------------------------------------------
-- 2. 项目统计（管理员"数据统计与报表"、"生成统计图表"数据来源）
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_project_statistics AS
SELECT p.project_id,
       p.project_no,
       p.title,
       p.category,
       p.status,
       p.target_amount,
       p.current_amount,
       ROUND(p.current_amount / NULLIF(p.target_amount, 0) * 100, 2) AS funding_percent,
       p.donor_count,
       p.view_count,
       p.favorite_count,
       p.share_count,
       p.progress_percent,
       (SELECT COUNT(*) FROM donation d WHERE d.project_id = p.project_id AND d.status = 'CANCELLED')    AS cancelled_donation_count,
       (SELECT COUNT(*) FROM fund_usage fu WHERE fu.project_id = p.project_id AND fu.audit_status = 0)   AS pending_usage_count,
       (SELECT COUNT(*) FROM evidence_record e WHERE e.project_id = p.project_id AND e.status = 'SUCCESS') AS evidence_count,
       (SELECT COUNT(*) FROM complaint cp WHERE cp.project_id = p.project_id AND cp.status IN (0, 1))    AS open_complaint_count,
       (SELECT COUNT(*) FROM warning_record wr WHERE wr.project_id = p.project_id AND wr.handle_status = 0) AS open_warning_count,
       p.create_time
FROM project p
WHERE p.deleted = 0;

-- -------------------------------------------------------------------------------------
-- 3. 存证明细（链上凭证 + 链下文件对照，支撑哈希核验）
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_evidence_detail AS
SELECT e.evidence_id,
       e.evidence_no,
       e.tx_id,
       e.block_height,
       e.chain_time,
       e.data_hash,
       e.biz_type,
       e.biz_id,
       e.project_id,
       p.title       AS project_title,
       e.file_id,
       f.original_name AS file_name,
       f.file_path,
       f.file_hash,
       e.function_name,
       e.operator_name,
       e.status,
       e.chain_source,
       CASE
           WHEN e.file_id IS NULL THEN 'NO_FILE'
           ELSE 'REQUIRES_FILE_HASH_VERIFICATION'
           END       AS hash_match_flag,
       e.create_time
FROM evidence_record e
         LEFT JOIN project p ON p.project_id = e.project_id
         LEFT JOIN file_record f ON f.file_id = e.file_id;

-- -------------------------------------------------------------------------------------
-- 4. 捐赠溯源（每笔捐赠 -> 存证凭证 -> 项目 -> 捐赠人，捐赠者"查看捐赠记录"）
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_donation_trace AS
SELECT d.donation_id,
       d.donation_no,
       d.project_id,
       p.title    AS project_title,
       p.status   AS project_status,
       d.donor_id,
       u.real_name AS donor_name,
       d.amount,
       d.donate_time,
       d.status   AS donation_status,
       d.cancel_deadline,
       d.anonymous,
       d.tx_id,
       d.block_height,
       d.data_hash,
       e.evidence_no,
       e.chain_time,
       e.chain_source
FROM donation d
         JOIN project p ON p.project_id = d.project_id
         JOIN sys_user u ON u.user_id = d.donor_id
         LEFT JOIN evidence_record e
                   ON e.biz_type = 'DONATION' AND e.biz_id = CAST(d.donation_id AS CHAR)
WHERE d.deleted = 0;

-- -------------------------------------------------------------------------------------
-- 5. 资金全链路（捐赠 → 项目 → 资金提取 → 资金使用凭证，监管方"资金链追踪"）
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_fund_chain AS
SELECT d.donation_id,
       d.project_id,
       p.title          AS project_title,
       d.donor_id,
       d.amount         AS donation_amount,
       d.donate_time,
       d.tx_id          AS donation_tx_id,
       d.block_height   AS donation_block_height,
       w.withdrawal_id,
       w.amount         AS withdrawal_amount,
       w.status         AS withdrawal_status,
       w.tx_id          AS withdrawal_tx_id,
       fu.usage_id,
       fu.amount        AS usage_amount,
       fu.audit_status  AS usage_audit_status,
       fu.tx_id         AS usage_tx_id,
       fu.file_hash     AS usage_file_hash
FROM donation d
         JOIN project p ON p.project_id = d.project_id
         LEFT JOIN fund_withdrawal w
                   ON w.project_id = d.project_id AND w.deleted = 0 AND w.status IN (1, 3)
         LEFT JOIN fund_usage fu
                   ON fu.project_id = d.project_id AND fu.deleted = 0 AND fu.audit_status = 1
WHERE d.deleted = 0 AND d.status = 'CONFIRMED' AND d.confirmation_id IS NOT NULL AND d.refund_status <> 'REFUNDED';

-- -------------------------------------------------------------------------------------
-- 6. 监管审计工作台（项目真实性核验 + 存证 + 预警 + 投诉，一屏总览）
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_audit_workbench AS
SELECT p.project_id,
       p.project_no,
       p.title,
       p.status,
       p.beneficiary_masked,
       p.target_amount,
       p.current_amount,
       c.real_name AS club_name,
       (SELECT COUNT(*) FROM project_material m WHERE m.project_id = p.project_id AND m.on_chain = 1)      AS on_chain_material_count,
       (SELECT COUNT(*) FROM evidence_record e WHERE e.project_id = p.project_id AND e.status = 'SUCCESS') AS evidence_count,
       (SELECT MAX(e.block_height) FROM evidence_record e WHERE e.project_id = p.project_id)              AS latest_block_height,
       (SELECT COUNT(*) FROM fund_usage fu WHERE fu.project_id = p.project_id)                            AS fund_usage_count,
       (SELECT COUNT(*) FROM warning_record wr WHERE wr.project_id = p.project_id AND wr.handle_status = 0) AS open_warning_count,
       (SELECT COUNT(*) FROM complaint cp WHERE cp.project_id = p.project_id AND cp.status IN (0, 1))     AS open_complaint_count,
       p.audit_time
FROM project p
         LEFT JOIN sys_user c ON c.user_id = p.club_id
WHERE p.deleted = 0;

