package com.campus.charity.module.finance;

import com.campus.charity.common.annotation.OpLog;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.HashUtil;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.fabric.dto.EvidencePayload;
import com.campus.charity.module.evidence.service.EvidenceChainService;
import com.campus.charity.module.file.entity.FileRecord;
import com.campus.charity.module.file.service.FileService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 财务唯一入账入口。锁与唯一索引共同保护并发；业务事务中只写存证任务。 */
@Service
public class FinanceService {
    private final JdbcTemplate db;
    private final EvidenceChainService evidence;
    private final FileService files;
    private final SimulatedPaymentReceiptService receipts;
    private final com.campus.charity.module.user.service.CampusIdentityPolicy identities;
    private final org.springframework.context.ApplicationEventPublisher events;
    public FinanceService(JdbcTemplate db, EvidenceChainService evidence, FileService files, org.springframework.context.ApplicationEventPublisher events, com.campus.charity.module.user.service.CampusIdentityPolicy identities, SimulatedPaymentReceiptService receipts) {
        this.db=db; this.evidence=evidence; this.files=files; this.events=events; this.identities=identities;
        this.receipts=receipts;
    }
    @Transactional @PreAuthorize("hasRole('STUDENT')")
    @OpLog(module="模拟订单", value="创建模拟捐赠订单", saveParams=false)
    public Map<String,Object> create(FinanceForms.Order f) {
        Long uid=SecurityUtil.getUserId();
        Map<String,Object> user=one("SELECT * FROM sys_user WHERE user_id=? AND deleted=0 FOR UPDATE",uid);
        if (identities.effectiveStatus(((Number)user.get("authStatus")).intValue(), ((Number)user.get("identityTestOnly")).intValue()==1)!=2) fail(identities.isTestMode()?"请先完成校园测试认证":"请先完成学校校园身份审核");
        Map<String,Object> old=optional("SELECT * FROM donation_order WHERE donor_id=? AND request_key=?",uid,f.getRequestKey());
        if (old!=null) {
            if (!sameNumber(old.get("projectId"),f.getProjectId()) || money(old).compareTo(f.getAmount())!=0
                    || !Objects.equals(intValue(old.get("anonymous")),Boolean.TRUE.equals(f.getAnonymous())?1:0)
                    || !Objects.equals(old.get("remark"),f.getRemark())) fail("幂等键已被不同订单内容使用");
            return enrichOrder(old);
        }
        positive(f.getAmount());
        Map<String,Object> p=one("SELECT * FROM project WHERE project_id=? AND deleted=0 FOR UPDATE",f.getProjectId());
        requireOpen(p,LocalDateTime.now());
        long id=insert("INSERT INTO donation_order(order_no,project_id,donor_id,amount,anonymous,remark,request_key,status,create_time,update_time) VALUES(?,?,?,?,?,?,?,'CREATED',NOW(),NOW())",
                number("ORD"),f.getProjectId(),uid,f.getAmount(),Boolean.TRUE.equals(f.getAnonymous())?1:0,f.getRemark(),f.getRequestKey());
        return enrichOrder(order(id,false));
    }
    @Transactional @PreAuthorize("hasRole('STUDENT')")
    @OpLog(module="模拟订单",value="登记模拟支付结果",saveParams=false)
    public Map<String,Object> pay(Long id, FinanceForms.Payment f) {
        Map<String,Object> o=order(id,true); owner(o,"donorId");
        if ("SUCCESS".equals(o.get("paymentResult"))) {
            if (!"SUCCESS".equals(f.getResult()) || (f.getVoucherFileId()!=null&&!Objects.equals(o.get("voucherFileId"),f.getVoucherFileId()))) fail("订单已支付，不能修改支付结果或凭证");
            return enrichOrder(o);
        }
        if (!Arrays.asList("CREATED","PAY_FAILED").contains(o.get("status"))) fail("当前订单状态不能支付");
        if ("FAILED".equals(f.getResult())) {
            db.update("UPDATE donation_order SET status='PAY_FAILED',payment_result='FAILED',update_time=NOW() WHERE order_id=?",id);
        } else {
            requireOpen(one("SELECT * FROM project WHERE project_id=? AND deleted=0 FOR UPDATE",o.get("projectId")),LocalDateTime.now());
            Long voucherId=f.getVoucherFileId()==null?createTestReceipt(o,f.getRemark()):f.getVoucherFileId();
            voucher(voucherId,SecurityUtil.getUserId());
            db.update("UPDATE donation_order SET status='WAIT_CONFIRM',payment_result='SUCCESS',voucher_file_id=?,paid_time=NOW(),update_time=NOW() WHERE order_id=?",voucherId,id);
        }
        return enrichOrder(order(id,false));
    }
    @Transactional @PreAuthorize("hasRole('STUDENT')")
    @OpLog(module="模拟订单",value="重新提交模拟支付审核",saveParams=false)
    public Map<String,Object> resubmit(Long id,FinanceForms.Resubmit f) {
        Map<String,Object> o=order(id,true); owner(o,"donorId");
        if("WAIT_CONFIRM".equals(o.get("status"))&&(f.getVoucherFileId()==null||Objects.equals(o.get("voucherFileId"),f.getVoucherFileId()))&&Objects.equals(o.get("remark"),f.getRemark()))return enrichOrder(o);
        if (!"FINANCE_REJECTED".equals(o.get("status"))) fail("只有财务驳回的订单可以重新提交");
        Long voucherId=f.getVoucherFileId()==null?createTestReceipt(o,f.getRemark()):f.getVoucherFileId();
        voucher(voucherId,SecurityUtil.getUserId());
        db.update("UPDATE donation_order SET status='WAIT_CONFIRM',voucher_file_id=?,remark=?,update_time=NOW() WHERE order_id=?",voucherId,f.getRemark(),id);
        return enrichOrder(order(id,false));
    }
    public Map<String,Object> orderDetail(Long id) {
        Map<String,Object> o=order(id,false);
        if (!SecurityUtil.isAdmin() && !SecurityUtil.isAuditor()) owner(o,"donorId");
        Map<String,Object> result=new LinkedHashMap<>(); result.put("order",enrichOrder(o));
        result.put("payment",map("result",o.get("paymentResult"),"paidTime",o.get("paidTime"),"voucherFileId",o.get("voucherFileId"),"simulated",true));
        result.put("confirmations",rows("SELECT * FROM finance_confirmation WHERE order_id=? ORDER BY confirmation_id",id));
        return result;
    }
    public Map<String,Object> orders(boolean finance,int current,int size,String status,Long projectId) {
        List<Object> args=new ArrayList<>(); String where=" WHERE 1=1";
        if (!finance) {where+=" AND o.donor_id=?";args.add(SecurityUtil.getUserId());}
        if (status!=null&&!status.isEmpty()) {
            if("REFUNDING".equals(status)||"REFUNDED".equals(status)){where+=" AND d.refund_status=?";args.add("REFUNDING".equals(status)?"PROCESSING":"REFUNDED");}
            else {where+=" AND o.status=?";args.add(status);}
        }
        if (projectId!=null) {where+=" AND o.project_id=?";args.add(projectId);}
        Map<String,Object> result=page("donation_order o JOIN project p ON p.project_id=o.project_id LEFT JOIN donation d ON d.order_id=o.order_id AND d.deleted=0","o.*,p.title AS project_title,d.donation_id,d.refund_status,CASE d.refund_status WHEN 'PROCESSING' THEN 'REFUNDING' WHEN 'REFUNDED' THEN 'REFUNDED' ELSE o.status END AS display_status",where,"o.order_id",args,current,size);
        for(Object record:(List<?>)result.get("records")){@SuppressWarnings("unchecked") Map<String,Object> row=(Map<String,Object>)record;putEvidence(row,"DONATION",row.get("donationId"));}
        return result;
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    @OpLog(module="财务确认",value="审核模拟捐赠订单",saveParams=false)
    public Map<String,Object> confirm(FinanceForms.Confirmation f) {
        Map<String,Object> o=order(f.getOrderId(),true);
        Map<String,Object> prior=optional("SELECT * FROM finance_confirmation WHERE request_key=?",f.getRequestKey());
        if (prior!=null) {
            if (!sameNumber(prior.get("orderId"),f.getOrderId()) || !Objects.equals(prior.get("result"),f.getResult())
                    || ((BigDecimal)prior.get("checkedAmount")).compareTo(f.getCheckedAmount())!=0
                    || !Objects.equals(prior.get("voucherFileId"),f.getVoucherFileId()) || !Objects.equals(prior.get("opinion"),f.getOpinion())
                    || !sameNumber(prior.get("operatorId"),SecurityUtil.getUserId())) fail("幂等键对应的确认内容不一致");
            return confirmationResult(prior,o);
        }
        if (!"WAIT_CONFIRM".equals(o.get("status"))||!"SUCCESS".equals(o.get("paymentResult"))) fail("订单不是待财务确认的成功支付订单");
        if (money(o).compareTo(f.getCheckedAmount())!=0) fail("财务核对金额必须与订单金额完全一致");
        if ("REJECT".equals(f.getResult())&&blank(f.getOpinion())) fail("驳回必须填写审核意见");
        Map<String,Object> p=one("SELECT * FROM project WHERE project_id=? AND deleted=0 FOR UPDATE",o.get("projectId"));
        if (!Arrays.asList("ONLINE","FINISH_PENDING").contains(p.get("status"))) fail("项目已下架或不允许继续财务确认");
        // 按原始支付时间检查筹款期限，而不是按财务点击时间判断。
        within(p,date(o.get("paidTime")));
        if ("PASS".equals(f.getResult())) {
            if (!Objects.equals(o.get("voucherFileId"),f.getVoucherFileId())) fail("请核对订单当前支付凭证");
            voucher(f.getVoucherFileId(),((Number)o.get("donorId")).longValue());
        }
        long confirmationId=insert("INSERT INTO finance_confirmation(order_id,result,checked_amount,voucher_file_id,opinion,operator_id,request_key,confirm_time) VALUES(?,?,?,?,?,?,?,NOW())",
                f.getOrderId(),f.getResult(),f.getCheckedAmount(),f.getVoucherFileId(),f.getOpinion(),SecurityUtil.getUserId(),f.getRequestKey());
        if ("REJECT".equals(f.getResult())) {
            db.update("UPDATE donation_order SET status='FINANCE_REJECTED',update_time=NOW() WHERE order_id=?",f.getOrderId());
        } else {
            long donationId=insert("INSERT INTO donation(donation_no,project_id,donor_id,amount,donate_time,pay_channel,status,anonymous,remark,order_id,confirmation_id,confirm_user_id,confirm_time,refund_status,deleted,create_time,update_time) VALUES(?,?,?,?,?,'MOCK','CONFIRMED',?,?,?,?,?,NOW(),'NONE',0,NOW(),NOW())",
                    number("DON"),o.get("projectId"),o.get("donorId"),money(o),o.get("paidTime"),o.get("anonymous"),o.get("remark"),f.getOrderId(),confirmationId,SecurityUtil.getUserId());
            db.update("UPDATE donation_order SET status='CONFIRMED',update_time=NOW() WHERE order_id=?",f.getOrderId());
            recalculate(o.get("projectId"));
            String hash=HashUtil.sha256Hex("DONATION_CONFIRM|v1|"+donationId+"|"+f.getOrderId()+"|"+o.get("projectId")+"|"+money(o).setScale(2).toPlainString()+"|"+date(o.get("paidTime")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)+"|"+confirmationId);
            db.update("UPDATE donation SET data_hash=? WHERE donation_id=?",hash,donationId);
            queue("DONATION",donationId,o.get("projectId"),"CONFIRMED",hash,money(o),null);
            events.publishEvent(new DonationConfirmed(donationId));
        }
        return confirmationResult(one("SELECT * FROM finance_confirmation WHERE confirmation_id=?",confirmationId),order(f.getOrderId(),false));
    }
    @Transactional @PreAuthorize("hasRole('STUDENT')")
    @OpLog(module="退款",value="申请全额模拟退款",saveParams=false)
    public Map<String,Object> applyRefund(FinanceForms.Refund f) {
        Map<String,Object> d=donation(f.getDonationId(),true); owner(d,"donorId");
        Map<String,Object> old=optional("SELECT * FROM refund_application WHERE applicant_id=? AND request_key=?",SecurityUtil.getUserId(),f.getRequestKey());
        if(old!=null) {
            if(!sameNumber(old.get("donationId"),f.getDonationId()) || !Objects.equals(old.get("reason"),f.getReason())) fail("幂等键对应的退款申请不一致");
            return old;
        }
        if(!"CONFIRMED".equals(d.get("status"))||d.get("confirmationId")==null) fail("仅新版财务确认的有效捐赠可申请退款；历史记录请联系学校管理人员");
        if(!"NONE".equals(d.get("refundStatus"))) fail("该捐赠已申请退款或已退款");
        long id=insert("INSERT INTO refund_application(refund_no,donation_id,project_id,applicant_id,amount,reason,request_key,status,create_time,update_time) VALUES(?,?,?,?,?,?,?,'PENDING',NOW(),NOW())",
                number("REF"),f.getDonationId(),d.get("projectId"),SecurityUtil.getUserId(),money(d),f.getReason(),f.getRequestKey());
        db.update("UPDATE donation SET refund_status='PROCESSING',update_time=NOW() WHERE donation_id=?",f.getDonationId());
        return refund(id,false);
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    @OpLog(module="退款",value="审核模拟退款",saveParams=false)
    public Map<String,Object> auditRefund(Long id,FinanceForms.Audit f) {
        Map<String,Object> initial=refund(id,false);
        donation(((Number)initial.get("donationId")).longValue(),true);
        Map<String,Object> r=refund(id,true);
        String next="PASS".equals(f.getResult())?"WAIT_REFUND":"REJECTED";
        if(!"PENDING".equals(r.get("status"))) {
            if(next.equals(r.get("status"))&&Objects.equals(r.get("opinion"),f.getOpinion())) return r;
            fail("该退款申请已经审核");
        }
        if("REJECT".equals(f.getResult())&&blank(f.getOpinion())) fail("驳回必须填写原因");
        db.update("UPDATE refund_application SET status=?,audit_user_id=?,audit_time=NOW(),opinion=?,update_time=NOW() WHERE refund_id=?",next,SecurityUtil.getUserId(),f.getOpinion(),id);
        if("REJECTED".equals(next)) db.update("UPDATE donation SET refund_status='NONE',update_time=NOW() WHERE donation_id=?",r.get("donationId"));
        return refund(id,false);
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    @OpLog(module="退款",value="登记模拟退款完成",saveParams=false)
    public Map<String,Object> completeRefund(Long id,FinanceForms.Complete f) {
        Map<String,Object> initial=refund(id,false);
        // 所有金额更新先锁项目，避免不同捐赠退款交叉等待项目与捐赠行。
        one("SELECT * FROM project WHERE project_id=? AND deleted=0 FOR UPDATE",initial.get("projectId"));
        Map<String,Object> d=donation(((Number)initial.get("donationId")).longValue(),true);
        Map<String,Object> r=refund(id,true);
        if("REFUNDED".equals(r.get("status"))) {
            if(!Objects.equals(r.get("voucherFileId"),f.getVoucherFileId()) || !Objects.equals(date(r.get("completeTime")),f.getCompleteTime().withNano(0))) fail("已完成退款，重复请求的完成凭证或时间不同");
            return enrichRefund(r);
        }
        if(!"WAIT_REFUND".equals(r.get("status"))) fail("退款必须先审核通过");
        if(f.getCompleteTime().isAfter(LocalDateTime.now().plusMinutes(1))||f.getCompleteTime().isBefore(date(r.get("auditTime")))) fail("完成时间必须在审核之后且不能晚于当前时间");
        voucher(f.getVoucherFileId(),SecurityUtil.getUserId());
        if(!"PROCESSING".equals(d.get("refundStatus"))) fail("原捐赠退款状态冲突");
        db.update("UPDATE refund_application SET status='REFUNDED',voucher_file_id=?,complete_user_id=?,complete_time=?,update_time=NOW() WHERE refund_id=?",f.getVoucherFileId(),SecurityUtil.getUserId(),f.getCompleteTime().withNano(0),id);
        db.update("UPDATE donation SET refund_status='REFUNDED',update_time=NOW() WHERE donation_id=?",r.get("donationId"));
        recalculate(r.get("projectId"));
        String hash=HashUtil.sha256Hex("REFUND_COMPLETE|v1|"+id+"|"+r.get("donationId")+"|"+r.get("projectId")+"|"+money(r).setScale(2).toPlainString()+"|"+f.getVoucherFileId()+"|"+f.getCompleteTime().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        queue("REFUND",id,r.get("projectId"),"REFUNDED",hash,money(r),f.getVoucherFileId());
        return enrichRefund(refund(id,false));
    }
    public Map<String,Object> refundDetail(Long id) {
        Map<String,Object> r=refund(id,false);
        if(!SecurityUtil.isAdmin()&&!SecurityUtil.isAuditor()) owner(r,"applicantId");
        return enrichRefund(r);
    }
    public Map<String,Object> refunds(boolean finance,int current,int size,String status,Long projectId) {
        List<Object> args=new ArrayList<>(); String where=" WHERE 1=1";
        if(!finance){where+=" AND r.applicant_id=?";args.add(SecurityUtil.getUserId());}
        if(status!=null&&!status.isEmpty()){where+=" AND r.status=?";args.add(status);}
        if(projectId!=null){where+=" AND r.project_id=?";args.add(projectId);}
        Map<String,Object> result=page("refund_application r JOIN project p ON p.project_id=r.project_id","r.*,p.title AS project_title",where,"r.refund_id",args,current,size);
        for(Object record:(List<?>)result.get("records")){@SuppressWarnings("unchecked") Map<String,Object> row=(Map<String,Object>)record;putEvidence(row,"REFUND",row.get("refundId"));}
        return result;
    }
    private void recalculate(Object projectId) {
        // project 行已由确认/完成流程加锁；统计口径唯一，退款审核不提前减款。
        db.update("UPDATE project p SET current_amount=(SELECT COALESCE(SUM(amount),0) FROM donation WHERE project_id=p.project_id AND deleted=0 AND status='CONFIRMED' AND confirmation_id IS NOT NULL AND refund_status<>'REFUNDED'),donor_count=(SELECT COUNT(DISTINCT donor_id) FROM donation WHERE project_id=p.project_id AND deleted=0 AND status='CONFIRMED' AND confirmation_id IS NOT NULL AND refund_status<>'REFUNDED'),update_time=NOW() WHERE project_id=?",projectId);
    }
    private void queue(String type,long id,Object projectId,String state,String hash,BigDecimal amount,Long fileId) {
        evidence.submit(EvidencePayload.builder().eventKey(type+":"+id+":"+state+":1").eventType(state).bizType(type).bizId(String.valueOf(id)).projectId(String.valueOf(projectId)).dataHash(hash).amount(amount.setScale(2).toPlainString()).status(state).operatorRole("ADMIN").operator("SCHOOL_FINANCE").operatorId(SecurityUtil.getUserId()).fileId(fileId).functionName("REFUND".equals(type)?"CreateRefundEvidence":"CreateDonationEvidence").build());
    }
    private Map<String,Object> confirmationResult(Map<String,Object> c,Map<String,Object> o) {
        Map<String,Object> d=optional("SELECT * FROM donation WHERE order_id=?",o.get("orderId"));
        Map<String,Object> result=map("confirmationId",c.get("confirmationId"),"donationId",d==null?null:d.get("donationId"),"orderStatus",o.get("status"));
        result.put("evidenceStatus",d==null?"NOT_REQUIRED":taskStatus("DONATION",d.get("donationId")));
        return result;
    }
    private Map<String,Object> enrichOrder(Map<String,Object> o) {
        Map<String,Object> d=optional("SELECT donation_id,refund_status FROM donation WHERE order_id=? AND deleted=0",o.get("orderId"));
        String refund=d==null?null:(String)d.get("refundStatus");
        o.put("donationId",d==null?null:d.get("donationId"));o.put("refundStatus",refund);
        o.put("displayStatus","PROCESSING".equals(refund)?"REFUNDING":"REFUNDED".equals(refund)?"REFUNDED":o.get("status"));
        Map<String,Object> p=optional("SELECT title FROM project WHERE project_id=?",o.get("projectId"));
        o.put("projectTitle",p==null?null:p.get("title"));
        putEvidence(o,"DONATION",d==null?null:d.get("donationId"));
        o.put("simulated",true);return o;
    }
    private Map<String,Object> enrichRefund(Map<String,Object> r) {
        Map<String,Object> p=optional("SELECT title FROM project WHERE project_id=?",r.get("projectId"));
        r.put("projectTitle",p==null?null:p.get("title"));
        putEvidence(r,"REFUND",r.get("refundId"));return r;
    }
    private void putEvidence(Map<String,Object> r,String type,Object id) {
        Map<String,Object> t=id==null?null:optional("SELECT status,tx_id,block_height,error_msg FROM evidence_task WHERE biz_type=? AND biz_id=? ORDER BY task_id DESC LIMIT 1",type,String.valueOf(id));
        r.put("evidenceStatus",t==null?"NOT_REQUIRED":t.get("status"));
        r.put("evidenceTxId",t==null?null:t.get("txId"));
        r.put("evidenceBlockHeight",t==null?null:t.get("blockHeight"));
        r.put("evidenceError",t==null?null:t.get("errorMsg"));
    }
    private String taskStatus(String type,Object id) {
        Map<String,Object> t=optional("SELECT status FROM evidence_task WHERE biz_type=? AND biz_id=? ORDER BY task_id DESC LIMIT 1",type,String.valueOf(id));
        return t==null?"NOT_REQUIRED":String.valueOf(t.get("status"));
    }
    private Long createTestReceipt(Map<String,Object> order,String remark) {
        return receipts.create(((Number)order.get("orderId")).longValue(),String.valueOf(order.get("orderNo")),
                ((Number)order.get("projectId")).longValue(),((Number)order.get("donorId")).longValue(),money(order),remark);
    }
    private void voucher(Long id,Long expectedUploader) {
        if(id==null) fail("请上传凭证文件");
        FileRecord f=files.getById(id);
        if(!Objects.equals(f.getUploaderId(),expectedUploader)) throw new BizException(ResultCode.FORBIDDEN,"凭证必须由对应业务人员上传");
        if(!Files.isRegularFile(Paths.get(f.getFilePath()))) fail("凭证原件不可访问，请重新上传");
        try {if(!HashUtil.sha256File(new java.io.File(f.getFilePath())).equalsIgnoreCase(f.getFileHash())) fail("凭证原件与登记摘要不一致");}
        catch(java.io.IOException e){fail("凭证文件读取失败");}
    }
    private void requireOpen(Map<String,Object> p,LocalDateTime now) {if(!"ONLINE".equals(p.get("status"))) fail("项目尚未审核上线或已停止筹款");within(p,now);}
    private void within(Map<String,Object> p,LocalDateTime when) {if(when==null || (p.get("startTime")!=null&&when.isBefore(date(p.get("startTime")))) || (p.get("endTime")!=null&&when.isAfter(date(p.get("endTime"))))) fail("支付时间不在项目筹款期限内");}
    private void owner(Map<String,Object> o,String key) {if(!sameNumber(o.get(key),SecurityUtil.getUserId())) throw new BizException(ResultCode.FORBIDDEN,"只能访问本人业务记录");}
    private Map<String,Object> order(Long id,boolean lock){return one("SELECT * FROM donation_order WHERE order_id=?"+(lock?" FOR UPDATE":""),id);}
    private Map<String,Object> donation(Long id,boolean lock){return one("SELECT * FROM donation WHERE donation_id=? AND deleted=0"+(lock?" FOR UPDATE":""),id);}
    private Map<String,Object> refund(Long id,boolean lock){return one("SELECT * FROM refund_application WHERE refund_id=?"+(lock?" FOR UPDATE":""),id);}
    private Map<String,Object> one(String sql,Object...args){Map<String,Object> r=optional(sql,args);if(r==null)throw new BizException(ResultCode.BAD_REQUEST,"业务记录不存在");return r;}
    private Map<String,Object> optional(String sql,Object...args){List<Map<String,Object>> list=rows(sql,args);return list.isEmpty()?null:list.get(0);}
    private List<Map<String,Object>> rows(String sql,Object...args){
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> row:db.queryForList(sql,args)){Map<String,Object> r=new LinkedHashMap<>();row.forEach((k,v)->{StringBuilder b=new StringBuilder();boolean upper=false;for(char ch:k.toCharArray()){if(ch=='_'){upper=true;}else{b.append(upper?Character.toUpperCase(ch):ch);upper=false;}}r.put(b.toString(),v instanceof Timestamp?((Timestamp)v).toLocalDateTime():v);});result.add(r);}return result;
    }
    private Map<String,Object> page(String from,String columns,String where,String order,List<Object> args,int current,int size) {
        current=Math.max(1,current);size=Math.max(1,Math.min(size,100));
        long total=db.queryForObject("SELECT COUNT(*) FROM "+from+where,Long.class,args.toArray());
        List<Object> paged=new ArrayList<>(args);paged.add(size);paged.add((current-1)*size);
        return map("records",rows("SELECT "+columns+" FROM "+from+where+" ORDER BY "+order+" DESC LIMIT ? OFFSET ?",paged.toArray()),"total",total,"current",current,"size",size);
    }
    private long insert(String sql,Object...args){KeyHolder key=new GeneratedKeyHolder();db.update(c->{PreparedStatement s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();}
    private static Map<String,Object> map(Object...pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    private static String number(String prefix){return prefix+UUID.randomUUID().toString().replace("-","").substring(0,28).toUpperCase();}
    private static BigDecimal money(Map<String,Object> r){return (BigDecimal)r.get("amount");}
    private static int intValue(Object value){return ((Number)value).intValue();}
    private static boolean sameNumber(Object a,Object b){return a!=null&&b!=null&&a.toString().equals(b.toString());}
    private static LocalDateTime date(Object v){return v==null?null:v instanceof Timestamp?((Timestamp)v).toLocalDateTime():(LocalDateTime)v;}
    private static boolean blank(String s){return s==null||s.trim().isEmpty();}
    private static void positive(BigDecimal amount){if(amount==null||amount.signum()<=0||amount.scale()>2||amount.compareTo(new BigDecimal("9999999999.99"))>0)fail("金额须为正数，最多两位小数");}
    private static void fail(String message){throw new BizException(ResultCode.BAD_REQUEST,message);}
}
