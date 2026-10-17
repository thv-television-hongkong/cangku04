-- 增量升级；禁止在已有库运行 database/schema.sql（该旧脚本会重建表）。
CREATE TABLE IF NOT EXISTS donation_order (
 order_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_no VARCHAR(64) NOT NULL UNIQUE,
 project_id BIGINT NOT NULL, donor_id BIGINT NOT NULL,
 amount DECIMAL(12,2) NOT NULL, anonymous TINYINT NOT NULL DEFAULT 0,
 remark VARCHAR(500), request_key VARCHAR(80) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'CREATED', payment_result VARCHAR(16),
 voucher_file_id BIGINT, paid_time DATETIME, create_time DATETIME NOT NULL,
 update_time DATETIME NOT NULL,
 UNIQUE KEY uk_order_request(donor_id,request_key),
 KEY idx_order_queue(status,order_id), KEY idx_order_owner(donor_id,order_id),
 KEY idx_order_project(project_id,order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS finance_confirmation (
 confirmation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL, result VARCHAR(12) NOT NULL,
 checked_amount DECIMAL(12,2) NOT NULL, voucher_file_id BIGINT,
 opinion VARCHAR(1000), operator_id BIGINT NOT NULL,
 request_key VARCHAR(80) NOT NULL UNIQUE, confirm_time DATETIME NOT NULL,
 KEY idx_confirmation_order(order_id,confirmation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS refund_application (
 refund_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 refund_no VARCHAR(64) NOT NULL UNIQUE, donation_id BIGINT NOT NULL,
 project_id BIGINT NOT NULL, applicant_id BIGINT NOT NULL,
 amount DECIMAL(12,2) NOT NULL, reason VARCHAR(1000) NOT NULL,
 request_key VARCHAR(80) NOT NULL, status VARCHAR(24) NOT NULL,
 audit_user_id BIGINT, audit_time DATETIME, opinion VARCHAR(1000),
 voucher_file_id BIGINT, complete_user_id BIGINT, complete_time DATETIME,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 active_donation_id BIGINT GENERATED ALWAYS AS
   (CASE WHEN status IN ('PENDING','WAIT_REFUND','REFUNDED') THEN donation_id ELSE NULL END) STORED,
 UNIQUE KEY uk_refund_active(active_donation_id),
 UNIQUE KEY uk_refund_request(applicant_id,request_key),
 KEY idx_refund_queue(status,refund_id), KEY idx_refund_donation(donation_id,refund_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS evidence_task (
 task_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 event_key VARCHAR(180) NOT NULL UNIQUE, biz_type VARCHAR(32) NOT NULL,
 biz_id VARCHAR(64) NOT NULL, project_id BIGINT,
 event_type VARCHAR(40) NOT NULL, event_version INT NOT NULL DEFAULT 1,
 data_hash CHAR(64) NOT NULL, payload LONGTEXT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING', chain_source VARCHAR(16) NOT NULL,
 retry_count INT NOT NULL DEFAULT 0, error_msg VARCHAR(500),
 evidence_id BIGINT, tx_id VARCHAR(128), block_height BIGINT, duration_ms BIGINT,
 next_retry_time DATETIME, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_task_queue(status,next_retry_time,task_id), KEY idx_task_project(project_id,task_id),
 KEY idx_task_business(biz_type,biz_id,task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS schema_upgrade (
 version VARCHAR(64) NOT NULL PRIMARY KEY, applied_time DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS identity_review (
 review_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL, identity_no VARCHAR(64) NOT NULL,
 file_id BIGINT NULL, status INT NOT NULL DEFAULT 1,
 verification_mode VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
 opinion VARCHAR(1000), reviewer_id BIGINT, review_time DATETIME,
 create_time DATETIME NOT NULL, KEY idx_identity_user(user_id,review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
