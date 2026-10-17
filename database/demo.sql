-- Fictional demonstration data only. No personal data, donations or invented chain receipts.
-- All four demonstration account passwords are 123456 (BCrypt encoded below).
USE campus_charity;
SET NAMES utf8mb4;
INSERT INTO sys_user(user_id,account,password,real_name,role,identity_no,college,auth_status,status,remark) VALUES
(1,'admin','$2a$10$cWhzx1VcMB7b3KR8DV8eWeG4PQi7P1dHDe.ZenOiqNejWfbV00WWG','演示管理员','ADMIN','ADMIN-DEMO','演示学校',2,1,'公开测试账号，请在个人信息中修改密码'),
(2,'auditor','$2a$10$cWhzx1VcMB7b3KR8DV8eWeG4PQi7P1dHDe.ZenOiqNejWfbV00WWG','演示审计员','AUDITOR','AUDIT-DEMO','演示学校',2,1,'仅演示'),
(3,'club001','$2a$10$cWhzx1VcMB7b3KR8DV8eWeG4PQi7P1dHDe.ZenOiqNejWfbV00WWG','演示公益社团','CLUB','CLUB-DEMO','演示学校',2,1,'演示资质，不代表真实学校审核'),
(6,'20210001','$2a$10$cWhzx1VcMB7b3KR8DV8eWeG4PQi7P1dHDe.ZenOiqNejWfbV00WWG','演示学生','STUDENT','20210001','演示学院',0,1,'请完成内部测试校园认证');
INSERT INTO club_qualification(user_id,club_name,club_code,college,status,audit_user_id,audit_opinion,audit_time)
VALUES(3,'演示公益社团','CLUB-DEMO','演示学校',2,1,'预置测试资质，不代表学校正式审核',NOW());
INSERT INTO project(project_id,project_no,title,category,beneficiary,beneficiary_masked,target_amount,current_amount,start_time,end_time,status,club_id,description,funds_plan,audit_user_id,audit_opinion,audit_time)
VALUES(1,'DEMO-0001','校园爱心图书角（内部测试）','EDUCATION','虚构测试对象','演示同学',1000.00,0.00,DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 30 DAY),'ONLINE',3,'此项目为预置测试数据，不向社会募捐，不产生真实资金流转。可用它体验测试认证、模拟支付、财务确认和退款。','测试图书购置，所有金额均为模拟记账。',1,'预置演示项目，不代表正式学校审核',NOW());
