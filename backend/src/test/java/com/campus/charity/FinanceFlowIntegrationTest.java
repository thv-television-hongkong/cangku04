package com.campus.charity;

import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.HashUtil;
import com.campus.charity.fabric.MockChainClient;
import com.campus.charity.fabric.dto.EvidencePayload;
import com.campus.charity.module.evidence.service.EvidenceChainService;
import com.campus.charity.module.evidence.service.EvidenceTaskService;
import com.campus.charity.module.finance.FinanceForms;
import com.campus.charity.module.finance.FinanceService;
import com.campus.charity.security.LoginUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK, properties={
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3308/campus_charity?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
    "spring.datasource.username=root","spring.datasource.password=",
    "campus.init-default-users=false","campus.evidence.poll-enabled=false","campus.backup.enabled=false",
    "campus.file.upload-path=./target/integration-upload","campus.backup.dir=./target/integration-backup",
    "campus.backup.port=3308","campus.backup.password=","campus.backup.mysqldump-path=${TEST_MYSQLDUMP:mysqldump}",
    "logging.level.com.campus.charity=WARN"
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class FinanceFlowIntegrationTest {
    @Autowired com.campus.charity.module.user.service.CampusIdentityService identities;
    @Autowired com.campus.charity.module.user.service.CampusIdentityPolicy identityPolicy;
    @Autowired com.campus.charity.module.user.service.UserService users;
    @Autowired com.campus.charity.module.donation.service.DonationService donations;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.campus.charity.security.JwtUtil jwt;

    @Autowired FinanceService finance;
    @Autowired JdbcTemplate db;
    @Autowired EvidenceTaskService tasks;
    @Autowired EvidenceChainService evidence;
    @Autowired PlatformTransactionManager manager;
    @Autowired com.campus.charity.module.project.service.ProjectService projects;
    @Autowired com.campus.charity.module.admin.service.AdminService administration;
    @Autowired com.campus.charity.module.trace.service.TraceService trace;
    @Autowired com.campus.charity.module.audit.service.AuditReportService reports;
    @Autowired com.campus.charity.module.file.service.FileService fileService;
    @SpyBean MockChainClient chain;
    long student,admin,other,project,voucher;
    @BeforeEach void setup() throws Exception {
        student=user("STUDENT");admin=user("ADMIN");other=user("STUDENT");
        project=insert("INSERT INTO project(project_no,title,beneficiary,beneficiary_masked,target_amount,current_amount,start_time,end_time,status,club_id,deleted,create_time,update_time) VALUES(?, '集成验证项目','已脱敏','同学',100,0,DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 1 DAY),'ONLINE',?,0,NOW(),NOW())",key(),admin);
        voucher=file(student);login(student,"STUDENT");
    }
    @AfterEach void clear(){org.springframework.test.util.ReflectionTestUtils.setField(identityPolicy,"testMode",false);SecurityContextHolder.clearContext();reset(chain);}

    @Test void paymentFailureRejectionRetryConfirmationAndRefund() throws Exception {
        FinanceForms.Order f=order("45.67");Map<String,Object> o=finance.create(f);long id=id(o,"orderId");
        assertEquals(id,id(finance.create(f),"orderId"));
        f.setAmount(new BigDecimal("46.00"));assertThrows(BizException.class,()->finance.create(f));
        FinanceForms.Payment pay=new FinanceForms.Payment();pay.setResult("FAILED");finance.pay(id,pay);
        assertEquals(0,count("donation","project_id",project));assertAmount("0.00");
        pay.setResult("SUCCESS");pay.setVoucherFileId(voucher);assertEquals("WAIT_CONFIRM",finance.pay(id,pay).get("status"));
        assertEquals("WAIT_CONFIRM",finance.pay(id,pay).get("status"));assertAmount("0.00");
        login(admin,"ADMIN");FinanceForms.Confirmation c=confirmation(id,"45.67");c.setCheckedAmount(new BigDecimal("45.68"));assertThrows(BizException.class,()->finance.confirm(c));
        c.setCheckedAmount(new BigDecimal("45.67"));c.setResult("REJECT");assertThrows(BizException.class,()->finance.confirm(c));c.setOpinion("凭证说明不全");finance.confirm(c);
        assertEquals(0,count("donation","project_id",project));
        login(student,"STUDENT");FinanceForms.Resubmit re=new FinanceForms.Resubmit();re.setVoucherFileId(voucher);re.setRemark("补充说明");finance.resubmit(id,re);
        login(admin,"ADMIN");FinanceForms.Confirmation approved=confirmation(id,"45.67");Map<String,Object> confirmed=finance.confirm(approved);long d=id(confirmed,"donationId");
        assertEquals(d,id(finance.confirm(approved),"donationId"));assertEquals("PENDING",confirmed.get("evidenceStatus"));assertAmount("45.67");
        assertEquals(1,count("donation","order_id",id));
        long task=db.queryForObject("SELECT task_id FROM evidence_task WHERE biz_type='DONATION' AND biz_id=?",Long.class,String.valueOf(d));
        doThrow(new IllegalStateException("injected adapter failure")).when(chain).submitEvidence(any());tasks.process(task);
        assertEquals("FAILED",db.queryForObject("SELECT status FROM evidence_task WHERE task_id=?",String.class,task));assertAmount("45.67");
        doCallRealMethod().when(chain).submitEvidence(any());tasks.retry(task);tasks.process(task);tasks.process(task);
        assertEquals("SUBMITTED",db.queryForObject("SELECT status FROM evidence_task WHERE task_id=?",String.class,task));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM chain_evidence_snapshot WHERE event_key=?",Integer.class,"DONATION:"+d+":CONFIRMED:1"));
        assertNotNull(db.queryForObject("SELECT tx_id FROM donation WHERE donation_id=?",String.class,d));
        login(student,"STUDENT");FinanceForms.Refund r=refund(d);long rid=id(finance.applyRefund(r),"refundId");assertEquals(rid,id(finance.applyRefund(r),"refundId"));
        assertEquals("REFUNDING",((Map<?,?>)finance.orderDetail(id).get("order")).get("displayStatus"));
        login(admin,"ADMIN");FinanceForms.Audit a=new FinanceForms.Audit();a.setResult("REJECT");a.setOpinion("请补全原因");finance.auditRefund(rid,a);
        login(student,"STUDENT");r=refund(d);rid=id(finance.applyRefund(r),"refundId");
        login(admin,"ADMIN");a.setResult("PASS");a.setOpinion("同意");finance.auditRefund(rid,a);assertAmount("45.67");
        FinanceForms.Complete complete=new FinanceForms.Complete();complete.setVoucherFileId(file(admin));complete.setCompleteTime(LocalDateTime.now().withNano(0));
        finance.completeRefund(rid,complete);finance.completeRefund(rid,complete);assertAmount("0.00");
        assertEquals("REFUNDED",((Map<?,?>)finance.orderDetail(id).get("order")).get("displayStatus"));
        assertEquals("CONFIRMED",db.queryForObject("SELECT status FROM donation WHERE donation_id=?",String.class,d));
        assertEquals("REFUNDED",db.queryForObject("SELECT refund_status FROM donation WHERE donation_id=?",String.class,d));
        final long finalRid=rid;complete.setVoucherFileId(voucher);assertThrows(BizException.class,()->finance.completeRefund(finalRid,complete));
        login(student,"STUDENT");assertThrows(BizException.class,()->finance.applyRefund(refund(d)));
    }
    @Test void concurrentConfirmAndRefundCannotDoubleCount() throws Exception {
        long order=paidOrder("30.00");FinanceForms.Confirmation c=confirmation(order,"30.00");
        List<Map<String,Object>> result=parallel(8,()->{login(admin,"ADMIN");return finance.confirm(c);});
        assertEquals(1,result.stream().map(r->r.get("donationId")).distinct().count());assertAmount("30.00");
        long donation=id(result.get(0),"donationId");login(student,"STUDENT");FinanceForms.Refund f=refund(donation);
        List<Map<String,Object>> refunds=parallel(6,()->{login(student,"STUDENT");return finance.applyRefund(f);});
        assertEquals(1,refunds.stream().map(r->r.get("refundId")).distinct().count());long rid=id(refunds.get(0),"refundId");
        login(admin,"ADMIN");FinanceForms.Audit a=new FinanceForms.Audit();a.setResult("PASS");finance.auditRefund(rid,a);
        FinanceForms.Complete done=new FinanceForms.Complete();done.setVoucherFileId(file(admin));done.setCompleteTime(LocalDateTime.now().withNano(0));
        parallel(6,()->{login(admin,"ADMIN");return finance.completeRefund(rid,done);});assertAmount("0.00");
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM evidence_task WHERE biz_type='REFUND' AND biz_id=?",Integer.class,String.valueOf(rid)));
    }
    @Test void resourceAndFinancePermissionsAreEnforcedInServices() throws Exception {
        long id=paidOrder("10.00");login(other,"STUDENT");assertThrows(BizException.class,()->finance.orderDetail(id));
        FinanceForms.Payment p=new FinanceForms.Payment();p.setResult("FAILED");assertThrows(BizException.class,()->finance.pay(id,p));
        FinanceForms.Confirmation c=confirmation(id,"10.00");assertThrows(AccessDeniedException.class,()->finance.confirm(c));
        login(admin,"AUDITOR");assertThrows(AccessDeniedException.class,()->finance.confirm(c));
        login(admin,"ADMIN");assertNotNull(finance.orderDetail(id));
    }
    @Test void differentOrdersAndRefundsOnOneProjectKeepExactNetAmount() throws Exception {
        List<Long> orders=new ArrayList<>();for(int i=0;i<6;i++)orders.add(paidOrder("5.55"));
        List<FinanceForms.Confirmation> confirmations=new ArrayList<>();for(Long id:orders)confirmations.add(confirmation(id,"5.55"));
        ExecutorService pool=Executors.newFixedThreadPool(6);
        List<Long> donations=new ArrayList<>();
        try {
            List<Future<Map<String,Object>>> futures=new ArrayList<>();for(FinanceForms.Confirmation c:confirmations)futures.add(pool.submit(()->{login(admin,"ADMIN");return finance.confirm(c);}));
            for(Future<Map<String,Object>> future:futures)donations.add(id(future.get(20,TimeUnit.SECONDS),"donationId"));assertAmount("33.30");
            List<Long> refunds=new ArrayList<>();for(Long d:donations){login(student,"STUDENT");long id=id(finance.applyRefund(refund(d)),"refundId");login(admin,"ADMIN");FinanceForms.Audit a=new FinanceForms.Audit();a.setResult("PASS");finance.auditRefund(id,a);refunds.add(id);}
            FinanceForms.Complete complete=new FinanceForms.Complete();complete.setVoucherFileId(file(admin));complete.setCompleteTime(LocalDateTime.now().withNano(0));
            List<Future<Map<String,Object>>> finished=new ArrayList<>();for(Long id:refunds)finished.add(pool.submit(()->{login(admin,"ADMIN");return finance.completeRefund(id,complete);}));
            for(Future<Map<String,Object>> result:finished)assertEquals("REFUNDED",result.get(20,TimeUnit.SECONDS).get("status"));assertAmount("0.00");
        } finally {pool.shutdownNow();}
    }
    @Test void expiryUsesOriginalPaymentTimeAndOfflinePausesConfirmation() throws Exception {
        long id=paidOrder("12.00");db.update("UPDATE project SET end_time=NOW(),status='OFFLINE' WHERE project_id=?",project);
        login(admin,"ADMIN");FinanceForms.Confirmation c=confirmation(id,"12.00");assertThrows(BizException.class,()->finance.confirm(c));
        db.update("UPDATE project SET status='ONLINE' WHERE project_id=?",project);finance.confirm(c);assertAmount("12.00");
    }
    @Test void eventEnqueueRollsBackWithBusinessAndConflictingHashIsRejected() {
        login(admin,"ADMIN");String key="TEST:"+key();EvidencePayload p=payload(key,HashUtil.sha256Hex("a"));
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).execute(s->{evidence.submit(p);throw new IllegalStateException("rollback");}));
        assertEquals(0,count("evidence_task","event_key",key));
        evidence.submit(p);evidence.submit(p);assertEquals(1,count("evidence_task","event_key",key));
        assertThrows(BizException.class,()->evidence.submit(payload(key,HashUtil.sha256Hex("b"))));
    }
    @Test void invalidMoneyAndUnverifiedIdentityCannotCreateOrders() {
        FinanceForms.Order f=order("0.001");assertThrows(BizException.class,()->finance.create(f));
        f.setAmount(new BigDecimal("-1"));assertThrows(BizException.class,()->finance.create(f));
        db.update("UPDATE sys_user SET auth_status=1 WHERE user_id=?",student);f.setAmount(new BigDecimal("1.00"));assertThrows(BizException.class,()->finance.create(f));
    }
    @Test void uncertainSubmissionReconcilesExistingEventWithoutDuplicate() {
        login(admin,"ADMIN");String event="TEST:"+key();long task=evidence.submit(payload(event,HashUtil.sha256Hex("uncertain"))).getTaskId();
        doAnswer(invocation->{invocation.callRealMethod();throw new IllegalStateException("response lost after commit");}).when(chain).submitEvidence(any());
        tasks.process(task);assertEquals("FAILED",db.queryForObject("SELECT status FROM evidence_task WHERE task_id=?",String.class,task));
        assertEquals(1,count("chain_evidence_snapshot","event_key",event));
        doCallRealMethod().when(chain).submitEvidence(any());tasks.retry(task);tasks.process(task);
        assertEquals("SUBMITTED",db.queryForObject("SELECT status FROM evidence_task WHERE task_id=?",String.class,task));assertEquals(1,count("chain_evidence_snapshot","event_key",event));
    }
    @Test void verificationDistinguishesMissingUnavailableAndMismatch() {
        login(admin,"ADMIN");String event="TEST:"+key();String hash=HashUtil.sha256Hex("verify");long task=evidence.submit(payload(event,hash)).getTaskId();tasks.process(task);
        assertEquals("MATCH",evidence.verify("PROJECT_STATUS",String.valueOf(project),hash).getOutcome());
        assertEquals("MISMATCH",evidence.verify("PROJECT_STATUS",String.valueOf(project),HashUtil.sha256Hex("other")).getOutcome());
        assertEquals("NOT_FOUND",evidence.verify("PROJECT_STATUS","unknown",hash).getOutcome());
        doThrow(new IllegalStateException("adapter unavailable")).when(chain).queryEvidence(anyString(),anyString());
        assertEquals("UNAVAILABLE",evidence.verify("PROJECT_STATUS",String.valueOf(project),hash).getOutcome());
    }
    @Test void finishRequiresSchoolApprovalAndPendingProjectIsLocked() {
        long club=user("CLUB");db.update("UPDATE project SET club_id=? WHERE project_id=?",club,project);
        login(club,"CLUB");projects.finish(project);
        assertEquals("FINISH_PENDING",db.queryForObject("SELECT status FROM project WHERE project_id=?",String.class,project));
        login(admin,"ADMIN");projects.auditFinish(project,"REJECT","请补齐执行总结");
        assertEquals("ONLINE",db.queryForObject("SELECT status FROM project WHERE project_id=?",String.class,project));
        login(club,"CLUB");projects.finish(project);login(admin,"ADMIN");projects.auditFinish(project,"PASS","材料齐全");
        assertEquals("FINISHED",db.queryForObject("SELECT status FROM project WHERE project_id=?",String.class,project));
        db.update("UPDATE project SET status='PENDING' WHERE project_id=?",project);login(club,"CLUB");
        com.campus.charity.module.project.dto.ProjectDtos.ProjectForm f=new com.campus.charity.module.project.dto.ProjectDtos.ProjectForm();f.setProjectId(project);
        assertThrows(BizException.class,()->projects.update(f));
    }
    @Test void exportsAndComplaintsAreScopedToBusinessOwner() {
        String a=key(),b=key();db.update("INSERT INTO complaint(complaint_no,user_id,project_id,complaint_type,title,content,status,create_time,update_time) VALUES(?,?,?,'OTHER','本人的投诉','仅测试',0,NOW(),NOW())",a,student,project);
        db.update("INSERT INTO complaint(complaint_no,user_id,project_id,complaint_type,title,content,status,create_time,update_time) VALUES(?,?,?,'OTHER','他人的投诉','仅测试',0,NOW(),NOW())",b,other,project);
        login(student,"STUDENT");assertTrue(administration.pageComplaints(null,project,1,100).getRecords().stream().allMatch(c->c.getUserId().equals(student)));
        login(admin,"ADMIN");String own="TEST:"+key();tasks.process(evidence.submit(payload(own,HashUtil.sha256Hex("owned"))).getTaskId());
        db.update("UPDATE project SET status='DRAFT',club_id=? WHERE project_id=?",other,project);
        login(student,"STUDENT");com.campus.charity.module.trace.dto.TraceDtos.TraceQuery q=new com.campus.charity.module.trace.dto.TraceDtos.TraceQuery();q.setProjectId(project);
        assertTrue(trace.exportEvidence(q).isEmpty());
    }
    @Test void auditReportUsesHistoricalEventsAndNeverClaimsUnavailableIsMatch() {
        login(admin,"ADMIN");
        assertEquals("NO_EVIDENCE",reports.generate("PROJECT",project,null,null).getVerifyResult());
        tasks.process(evidence.submit(payload("TEST:"+key(),HashUtil.sha256Hex("version1"))).getTaskId());
        tasks.process(evidence.submit(payload("TEST:"+key(),HashUtil.sha256Hex("version2"))).getTaskId());
        com.campus.charity.module.audit.entity.AuditReport matched=reports.generate("PROJECT",project,null,null);
        assertEquals("ALL_MATCH",matched.getVerifyResult());assertTrue(matched.getSummary().contains("MOCK"));
        doThrow(new IllegalStateException("offline")).when(chain).queryByTxId(anyString());
        com.campus.charity.module.audit.entity.AuditReport unavailable=reports.generate("PROJECT",project,null,null);
        assertEquals("UNVERIFIED",unavailable.getVerifyResult());assertTrue(unavailable.getSummary().contains("暂不可用 2 条"));
        assertFalse(unavailable.getSummary().contains("未发现被篡改"));
    }
    @Test void financeConfirmationTriggersWarningOnceAfterCommit() throws Exception {
        long rule=insert("INSERT INTO audit_rule(rule_name,rule_type,biz_type,threshold_value,threshold_unit,comparison,warning_level,enabled,hit_count,deleted,create_time,update_time) VALUES(?,'SINGLE_AMOUNT','DONATION',1,'YUAN','>','HIGH',1,0,0,NOW(),NOW())","财务预警"+key());
        long id=paidOrder("10.00");assertEquals(0,count("warning_record","rule_id",rule));
        login(admin,"ADMIN");FinanceForms.Confirmation c=confirmation(id,"10.00");finance.confirm(c);finance.confirm(c);
        assertEquals(1,count("warning_record","rule_id",rule));
    }
    @Test void originalVoucherUploadIsStoredUnderConfiguredAbsoluteDirectory() throws Exception {
        login(student,"STUDENT");
        byte[] bytes="Isolated multipart voucher".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        com.campus.charity.module.file.dto.FileDtos.FileVO uploaded=fileService.upload(new org.springframework.mock.web.MockMultipartFile("file","voucher.txt","text/plain",bytes),"PAYMENT_VOUCHER","test");
        com.campus.charity.module.file.entity.FileRecord record=fileService.getById(uploaded.getFileId());
        Path stored=Paths.get(record.getFilePath());assertTrue(stored.isAbsolute());
        assertTrue(stored.normalize().startsWith(Paths.get("target/integration-upload").toAbsolutePath().normalize()));
        assertArrayEquals(bytes,Files.readAllBytes(stored));assertEquals(HashUtil.sha256Hex(bytes),record.getFileHash());
    }
    @Test void backupArchiveContainsDatabaseAndOriginalFiles() throws Exception {
        login(admin,"ADMIN");Path original=Paths.get("target/integration-upload/backup-check.txt");Files.createDirectories(original.getParent());Files.writeString(original,"original voucher backup check");
        com.campus.charity.module.admin.entity.SysBackupRecord backup=administration.runBackup("MANUAL");
        assertEquals("SUCCESS",backup.getStatus(),backup.getErrorMsg());
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(backup.getBackupPath())) {
            assertNotNull(zip.getEntry("upload/backup-check.txt"));
            assertTrue(zip.stream().anyMatch(e->e.getName().startsWith("database/")&&e.getSize()>0));
            assertEquals("original voucher backup check",new String(zip.getInputStream(zip.getEntry("upload/backup-check.txt")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    @Test void ordinaryOrderQueriesUnderConcurrentLoad() throws Exception {
        for(int i=0;i<8;i++)finance.create(order("1.00"));
        List<Long> times=parallel(80,()->{login(student,"STUDENT");long start=System.nanoTime();finance.orders(false,1,10,"",null);return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);});
        Collections.sort(times);long p95=times.get((int)Math.ceil(times.size()*.95)-1);assertTrue(p95<=2000,"查询 p95="+p95+"ms");
        try{Path report=Paths.get("target/query-performance.txt");Files.write(report,Arrays.asList("MySQL 8 local isolated database; 16 workers, 80 paged order queries; p95="+p95+"ms; max="+times.get(times.size()-1)+"ms; not a production-scale benchmark."));}catch(Exception e){throw new RuntimeException(e);}
    }
    @Test void newRegistrationCanTestCertifyWithoutFileAndCreateOrder() {
        testMode(true);
        com.campus.charity.module.user.dto.UserDtos.SendCodeDTO code=new com.campus.charity.module.user.dto.UserDtos.SendCodeDTO();code.setTarget("test"+key());code.setBizType("REGISTER");
        com.campus.charity.module.user.dto.UserDtos.RegisterDTO f=new com.campus.charity.module.user.dto.UserDtos.RegisterDTO();
        f.setAccount(code.getTarget());f.setCode(users.sendVerifyCode(code));f.setPassword("Test123456");f.setConfirmPassword("Test123456");f.setRole("STUDENT");
        com.campus.charity.module.user.dto.UserDtos.LoginVO registered=users.register(f);login(registered.getUserId(),"STUDENT");
        assertThrows(BizException.class,()->finance.create(order("1.00")));identities.submit(identityForm());
        assertEquals(2,users.currentUser().getAuthStatus());assertTrue(users.currentUser().getIdentityTestOnly());
        assertEquals("CREATED",finance.create(order("1.00")).get("status"));
        assertNull(db.queryForObject("SELECT file_id FROM identity_review WHERE user_id=?",Long.class,registered.getUserId()));
    }
    @Test void pendingManualApplicationConvertsWithoutDeletingOriginalAndConcurrentCertIsIdempotent() throws Exception {
        testMode(true);db.update("UPDATE sys_user SET auth_status=1 WHERE user_id=?",student);
        long original=insert("INSERT INTO identity_review(user_id,identity_no,file_id,status,create_time) VALUES(?,'OLD',?,1,NOW())",student,voucher);
        parallel(8,()->{login(student,"STUDENT");return identities.submit(identityForm());});
        assertEquals(2,count("identity_review","user_id",student));assertEquals(4,db.queryForObject("SELECT status FROM identity_review WHERE review_id=?",Integer.class,original));
        assertEquals(voucher,db.queryForObject("SELECT file_id FROM identity_review WHERE review_id=?",Long.class,original));
        login(student,"STUDENT");assertEquals(2,users.currentUser().getAuthStatus());finance.create(order("2.00"));
        login(admin,"ADMIN");assertThrows(BizException.class,()->identities.audit(original,true,"旧申请不能重新审核"));
    }
    @Test void manualModeRestorationBlocksTestCertificationAndRequiresSchoolReview() throws Exception {
        testMode(true);db.update("UPDATE sys_user SET auth_status=0 WHERE user_id=?",student);identities.submit(identityForm());String token=token(student,"STUDENT");
        testMode(false);assertEquals(0,users.currentUser().getAuthStatus());assertThrows(BizException.class,()->finance.create(order("1.00")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/user/profile").header("Authorization","Bearer "+token))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.authStatus").value(0));
        login(student,"STUDENT");assertThrows(BizException.class,()->identities.submit(identityForm()));
        db.update("UPDATE file_record SET biz_type='IDENTITY' WHERE file_id=?",voucher);
        com.campus.charity.module.user.service.CampusIdentityService.Submit f=identityForm();f.setFileId(voucher);identities.submit(f);
        long review=db.queryForObject("SELECT MAX(review_id) FROM identity_review WHERE user_id=?",Long.class,student);
        login(admin,"ADMIN");identities.audit(review,true,"学校核验通过");login(student,"STUDENT");
        assertEquals(2,users.currentUser().getAuthStatus());assertFalse(users.currentUser().getIdentityTestOnly());finance.create(order("1.00"));
    }
    @Test void identityInputConsentAndRoleAreValidatedAndOldJwtReadsFreshState() throws Exception {
        testMode(true);db.update("UPDATE sys_user SET auth_status=1 WHERE user_id=?",student);String token=token(student,"STUDENT");
        com.campus.charity.module.user.service.CampusIdentityService.Submit f=identityForm();
        f.setAcknowledged(false);assertThrows(BizException.class,()->identities.submit(f));f.setAcknowledged(true);f.setIdentityNo("   ");assertThrows(BizException.class,()->identities.submit(f));identities.submit(identityForm());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/user/profile").header("Authorization","Bearer "+token))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.authStatus").value(2))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.identityTestOnly").value(true));
        login(admin,"ADMIN");assertThrows(AccessDeniedException.class,()->identities.submit(identityForm()));
    }
    @Test void refundEligibilityAndEvidenceAreHonestIncludingLegacyAndFailedTasks() {
        long legacy=insert("INSERT INTO donation(donation_no,project_id,donor_id,amount,status,refund_status,deleted,create_time,update_time) VALUES(?,?,?,3,'CONFIRMED','NONE',0,NOW(),NOW())",key(),project,student);
        assertFalse(donations.detail(legacy).getRefundEligible());assertTrue(donations.detail(legacy).getRefundUnavailableReason().contains("历史记录"));
        assertEquals("NOT_REQUIRED",donations.detail(legacy).getEvidenceStatus());assertThrows(BizException.class,()->finance.applyRefund(refund(legacy)));
        long order=paidOrder("6.00");login(admin,"ADMIN");long d=id(finance.confirm(confirmation(order,"6.00")),"donationId");
        login(student,"STUDENT");assertTrue(donations.detail(d).getRefundEligible());db.update("UPDATE evidence_task SET status='FAILED' WHERE biz_type='DONATION' AND biz_id=?",String.valueOf(d));
        assertEquals("FAILED",donations.detail(d).getEvidenceStatus());assertEquals("FAILED",((Map<?,?>)finance.orderDetail(order).get("order")).get("evidenceStatus"));
        long rid=id(finance.applyRefund(refund(d)),"refundId");assertFalse(donations.detail(d).getRefundEligible());assertEquals("NOT_REQUIRED",finance.refundDetail(rid).get("evidenceStatus"));
        login(other,"STUDENT");assertThrows(BizException.class,()->finance.applyRefund(refund(d)));assertThrows(BizException.class,()->finance.refundDetail(rid));
    }
    @Test void rejectedOrUnverifiedUsersCanCertifyAndFormalCertificationIsPreserved() {
        testMode(true);
        for(int state:new int[]{0,1,3}){
            long uid=user("STUDENT");db.update("UPDATE sys_user SET auth_status=? WHERE user_id=?",state,uid);login(uid,"STUDENT");
            identities.submit(identityForm());assertEquals(2,users.currentUser().getAuthStatus());assertTrue(users.currentUser().getIdentityTestOnly());finance.create(order("1.00"));
        }
        login(student,"STUDENT");identities.submit(identityForm());assertEquals(2,users.currentUser().getAuthStatus());assertFalse(users.currentUser().getIdentityTestOnly());
        assertEquals(0,count("identity_review","user_id",student));
        long uid=user("STUDENT");db.update("UPDATE sys_user SET auth_status=0 WHERE user_id=?",uid);login(uid,"STUDENT");
        com.campus.charity.module.user.service.CampusIdentityService.Submit invalid=identityForm();invalid.setCollege(String.join("",Collections.nCopies(65,"学")));assertThrows(BizException.class,()->identities.submit(invalid));
    }
    @Test void paymentWithoutUploadSupportsFailureRetryConcurrentReplayAndFinanceRefund() throws Exception {
        long order=id(finance.create(order("9.87")),"orderId");
        FinanceForms.Payment pay=new FinanceForms.Payment();pay.setResult("FAILED");
        finance.pay(order,pay);assertAmount("0.00");assertEquals(0,receiptCount(order));
        pay.setResult("SUCCESS");pay.setRemark("无需上传凭证的内部测试");
        List<Map<String,Object>> results=parallel(6,()->{login(student,"STUDENT");return finance.pay(order,pay);});
        assertEquals(1,results.stream().map(r->r.get("voucherFileId")).distinct().count());assertEquals(1,receiptCount(order));
        long receipt=id(results.get(0),"voucherFileId");
        com.campus.charity.module.file.entity.FileRecord file=fileService.getById(receipt);
        String content=new String(Files.readAllBytes(Paths.get(file.getFilePath())),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(content.contains("内部测试"));assertTrue(content.contains("未发生实际资金扣款"));assertTrue(content.contains("9.87"));
        assertTrue(content.contains(String.valueOf(results.get(0).get("orderNo"))));
        assertEquals(HashUtil.sha256File(new java.io.File(file.getFilePath())),file.getFileHash());assertAmount("0.00");
        login(other,"STUDENT");assertThrows(BizException.class,()->finance.pay(order,pay));
        login(admin,"ADMIN");FinanceForms.Confirmation c=confirmation(order,"9.87");c.setVoucherFileId(receipt);
        c.setCheckedAmount(new BigDecimal("9.88"));assertThrows(BizException.class,()->finance.confirm(c));c.setCheckedAmount(new BigDecimal("9.87"));
        long donation=id(finance.confirm(c),"donationId");assertAmount("9.87");
        login(student,"STUDENT");finance.pay(order,pay);assertEquals(1,receiptCount(order));
        long refund=id(finance.applyRefund(refund(donation)),"refundId");login(admin,"ADMIN");
        FinanceForms.Audit a=new FinanceForms.Audit();a.setResult("PASS");finance.auditRefund(refund,a);assertAmount("9.87");
        FinanceForms.Complete done=new FinanceForms.Complete();done.setVoucherFileId(file(admin));done.setCompleteTime(LocalDateTime.now().withNano(0));
        finance.completeRefund(refund,done);finance.completeRefund(refund,done);assertAmount("0.00");
    }
    @Test void noUploadResubmitIsIdempotentAndRolledBackReceiptIsRemoved() throws Exception {
        long order=id(finance.create(order("4.56")),"orderId");FinanceForms.Payment pay=new FinanceForms.Payment();pay.setResult("SUCCESS");
        final Path[] temporary=new Path[1];
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).execute(s->{Map<String,Object> r=finance.pay(order,pay);temporary[0]=Paths.get(fileService.getById(id(r,"voucherFileId")).getFilePath());throw new IllegalStateException("rollback");}));
        assertEquals(0,receiptCount(order));assertFalse(Files.exists(temporary[0]));
        Map<String,Object> paid=finance.pay(order,pay);long oldReceipt=id(paid,"voucherFileId");
        login(admin,"ADMIN");FinanceForms.Confirmation reject=confirmation(order,"4.56");reject.setVoucherFileId(oldReceipt);reject.setResult("REJECT");reject.setOpinion("请补充测试说明");finance.confirm(reject);
        login(student,"STUDENT");FinanceForms.Resubmit re=new FinanceForms.Resubmit();re.setRemark("已补充测试说明");
        List<Map<String,Object>> results=parallel(6,()->{login(student,"STUDENT");return finance.resubmit(order,re);});
        assertEquals(1,results.stream().map(r->r.get("voucherFileId")).distinct().count());assertEquals(2,receiptCount(order));
        long currentReceipt=id(results.get(0),"voucherFileId");assertNotEquals(oldReceipt,currentReceipt);
        assertTrue(Files.exists(Paths.get(fileService.getById(oldReceipt).getFilePath())));
        login(admin,"ADMIN");FinanceForms.Confirmation pass=confirmation(order,"4.56");pass.setVoucherFileId(oldReceipt);assertThrows(BizException.class,()->finance.confirm(pass));
        pass.setVoucherFileId(currentReceipt);finance.confirm(pass);assertAmount("4.56");
    }
    private int receiptCount(long order){return db.queryForObject("SELECT COUNT(*) FROM file_record WHERE biz_type='PAYMENT_VOUCHER' AND biz_id=? AND deleted=0",Integer.class,String.valueOf(order));}
    private void testMode(boolean enabled){org.springframework.test.util.ReflectionTestUtils.setField(identityPolicy,"testMode",enabled);}
    private com.campus.charity.module.user.service.CampusIdentityService.Submit identityForm(){
        com.campus.charity.module.user.service.CampusIdentityService.Submit f=new com.campus.charity.module.user.service.CampusIdentityService.Submit();f.setIdentityNo("TEST20261017");f.setRealName("内部测试同学");f.setCollege("测试学院");f.setAcknowledged(true);return f;
    }
    private String token(long id,String role){LoginUser u=new LoginUser();u.setUserId(id);u.setAccount("TEST");u.setRole(role);u.setStatus(1);u.setAuthStatus(1);return jwt.generateToken(u);}

    private EvidencePayload payload(String event,String hash){return EvidencePayload.builder().eventKey(event).bizType("PROJECT_STATUS").bizId(String.valueOf(project)).projectId(String.valueOf(project)).dataHash(hash).status("ONLINE").build();}
    private long paidOrder(String amount){login(student,"STUDENT");long id=id(finance.create(order(amount)),"orderId");FinanceForms.Payment p=new FinanceForms.Payment();p.setResult("SUCCESS");p.setVoucherFileId(voucher);finance.pay(id,p);return id;}
    private FinanceForms.Order order(String amount){FinanceForms.Order f=new FinanceForms.Order();f.setProjectId(project);f.setAmount(new BigDecimal(amount));f.setRequestKey(key());return f;}
    private FinanceForms.Confirmation confirmation(long id,String amount){FinanceForms.Confirmation f=new FinanceForms.Confirmation();f.setOrderId(id);f.setResult("PASS");f.setCheckedAmount(new BigDecimal(amount));f.setVoucherFileId(voucher);f.setRequestKey(key());return f;}
    private FinanceForms.Refund refund(long d){FinanceForms.Refund r=new FinanceForms.Refund();r.setDonationId(d);r.setReason("测试全额模拟退款");r.setRequestKey(key());return r;}
    private void assertAmount(String amount){assertEquals(0,new BigDecimal(amount).compareTo(db.queryForObject("SELECT current_amount FROM project WHERE project_id=?",BigDecimal.class,project)));}
    private int count(String table,String column,Object id){return db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=?",Integer.class,id);}
    private long user(String role){return insert("INSERT INTO sys_user(account,password,real_name,role,identity_no,auth_status,status,deleted,create_time,update_time) VALUES(?,'disabled-for-test','测试用户',?,'TEST',2,1,0,NOW(),NOW())",key(),role);}
    private long file(long uploader) throws Exception {Path dir=Paths.get("target/integration-files");Files.createDirectories(dir);Path f=Files.write(dir.resolve(key()+".txt"),"simulated voucher".getBytes("UTF-8"));return insert("INSERT INTO file_record(file_no,original_name,stored_name,file_path,file_hash,biz_type,uploader_id,deleted,create_time,update_time) VALUES(?,'测试凭证.txt',?,?,?,'PAYMENT_VOUCHER',?,0,NOW(),NOW())",key(),f.getFileName().toString(),f.toAbsolutePath().toString(),HashUtil.sha256File(f.toFile()),uploader);}
    private long insert(String sql,Object...args){KeyHolder k=new GeneratedKeyHolder();db.update(conn->{PreparedStatement s=conn.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},k);return k.getKey().longValue();}
    private static String key(){return UUID.randomUUID().toString().replace("-","");}
    private static long id(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static void login(long id,String role){LoginUser u=new LoginUser();u.setUserId(id);u.setRole(role);u.setAccount("TEST");u.setRealName("测试");u.setAuthStatus(2);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));}
    private <T> List<T> parallel(int n,Callable<T> call) throws Exception {ExecutorService pool=Executors.newFixedThreadPool(Math.min(n,16));try{List<Future<T>> futures=new ArrayList<>();for(int i=0;i<n;i++)futures.add(pool.submit(()->{try{return call.call();}finally{SecurityContextHolder.clearContext();}}));List<T> r=new ArrayList<>();for(Future<T> f:futures)r.add(f.get(30,TimeUnit.SECONDS));return r;}finally{pool.shutdownNow();}}
}
