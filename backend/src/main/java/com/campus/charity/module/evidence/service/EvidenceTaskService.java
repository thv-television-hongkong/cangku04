package com.campus.charity.module.evidence.service;

import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.fabric.dto.ChainResultDTO;
import com.campus.charity.fabric.dto.EvidencePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** 持久化 outbox。轮询只读取已提交事务；失败保留业务结果，按事件键协调不确定的链上结果。 */
@Slf4j @Service @DependsOn("requirementsUpgrade")
public class EvidenceTaskService {
    private final JdbcTemplate db;
    private final EvidenceChainService evidence;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    @org.springframework.beans.factory.annotation.Value("${campus.evidence.poll-enabled:true}")
    private boolean pollEnabled;
    public EvidenceTaskService(JdbcTemplate db,EvidenceChainService evidence,ObjectMapper json,PlatformTransactionManager manager){this.db=db;this.evidence=evidence;this.json=json;this.transaction=new TransactionTemplate(manager);}
    @Scheduled(fixedDelayString="${campus.evidence.poll-ms:3000}",initialDelay=10000)
    public void poll(){
        if(!pollEnabled)return;
        List<Long> ids=db.queryForList("SELECT task_id FROM evidence_task WHERE status IN ('PENDING','FAILED') AND retry_count<5 AND (next_retry_time IS NULL OR next_retry_time<=NOW()) ORDER BY task_id LIMIT 10",Long.class);
        for(Long id:ids) process(id);
    }
    public synchronized void process(Long id){
        transaction.executeWithoutResult(s->{
            // 多应用实例也串行维护本地模拟哈希链，避免两个任务引用同一个前序块。
            db.queryForList("SELECT version FROM schema_upgrade WHERE version='20261017-finance' FOR UPDATE");
            Map<String,Object> task=db.queryForMap("SELECT * FROM evidence_task WHERE task_id=? FOR UPDATE",id);
            if("SUBMITTED".equals(task.get("status"))) return;
            long start=System.currentTimeMillis();
            try{
                EvidencePayload payload=json.readValue((String)task.get("payload"),EvidencePayload.class);
                ChainResultDTO receipt=evidence.submitCommitted(payload);
                Long evidenceId=db.queryForObject("SELECT evidence_id FROM evidence_record WHERE tx_id=? ORDER BY evidence_id DESC LIMIT 1",Long.class,receipt.getTxId());
                db.update("UPDATE evidence_task SET status='SUBMITTED',evidence_id=?,tx_id=?,block_height=?,error_msg=NULL,duration_ms=?,update_time=NOW() WHERE task_id=?",evidenceId,receipt.getTxId(),receipt.getBlockHeight(),System.currentTimeMillis()-start,id);
                backfill(payload,receipt,evidenceId);
            }catch(Exception e){
                // 不把远程异常文本、证书路径等敏感细节返回给普通用户。
                db.update("UPDATE evidence_task SET status='FAILED',retry_count=retry_count+1,error_msg='存证适配器提交失败，管理员可重试',next_retry_time=DATE_ADD(NOW(),INTERVAL 30 SECOND),duration_ms=?,update_time=NOW() WHERE task_id=?",System.currentTimeMillis()-start,id);
                log.warn("存证任务失败 taskId={} type={}",id,e.getClass().getSimpleName());
            }
        });
    }
    private void backfill(EvidencePayload p,ChainResultDTO r,Long evidenceId){
        String table=null,key=null;
        switch(p.getBizType()){
            case "PROJECT":case "PROJECT_STATUS":table="project";key="project_id";break;
            case "DONATION":table="donation";key="donation_id";break;
            case "FUND_USAGE":table="fund_usage";key="usage_id";break;
            case "MATERIAL":table="project_material";key="material_id";break;
            case "WITHDRAWAL":table="fund_withdrawal";key="withdrawal_id";break;
            case "PROJECT_PROGRESS":table="project_progress";key="progress_id";break;
            case "AUDIT_REPORT":table="audit_report";key="report_id";break;
            default:break;
        }
        // 非数字业务（如维护登记）没有对应实体行；历史事件晚到时不得覆盖当前版本回执。
        boolean latest=db.queryForObject("SELECT COUNT(*) FROM evidence_task WHERE biz_type=? AND biz_id=? AND task_id>(SELECT task_id FROM evidence_task WHERE event_key=?) AND status='SUBMITTED'",Integer.class,p.getBizType(),p.getBizId(),p.getEventKey())==0;
        if(table!=null && p.getBizId().matches("[0-9]+") && latest){
            db.update("UPDATE "+table+" SET tx_id=?,block_height=? WHERE "+key+"=?",r.getTxId(),r.getBlockHeight(),p.getBizId());
            if("MATERIAL".equals(p.getBizType())) db.update("UPDATE project_material SET on_chain=1 WHERE material_id=?",p.getBizId());
            if("DONATION".equals(p.getBizType())||"PROJECT".equals(p.getBizType())) db.update("UPDATE "+table+" SET evidence_id=? WHERE "+key+"=?",evidenceId,p.getBizId());
        }
        if(p.getFileId()!=null) db.update("UPDATE file_record SET on_chain=1,tx_id=?,block_height=? WHERE file_id=?",r.getTxId(),r.getBlockHeight(),p.getFileId());
    }
    public Map<String,Object> detail(Long id){
        Map<String,Object> t=db.queryForMap("SELECT task_id AS taskId,biz_type AS bizType,biz_id AS bizId,project_id AS projectId,status,chain_source AS chainSource,evidence_id AS evidenceId,tx_id AS txId,block_height AS blockHeight,retry_count AS retryCount,error_msg AS errorMsg FROM evidence_task WHERE task_id=?",id);
        if(!SecurityUtil.isAdmin()&&!SecurityUtil.isAuditor()){
            Long uid=SecurityUtil.getUserId();int allowed=0;
            if("DONATION".equals(t.get("bizType"))) allowed=db.queryForObject("SELECT COUNT(*) FROM donation WHERE donation_id=? AND donor_id=? AND deleted=0",Integer.class,t.get("bizId"),uid);
            if("REFUND".equals(t.get("bizType"))) allowed=db.queryForObject("SELECT COUNT(*) FROM refund_application WHERE refund_id=? AND applicant_id=?",Integer.class,t.get("bizId"),uid);
            if(allowed==0&&t.get("projectId")!=null&&SecurityUtil.isClub()) allowed=db.queryForObject("SELECT COUNT(*) FROM project WHERE project_id=? AND club_id=? AND deleted=0",Integer.class,t.get("projectId"),uid);
            if(allowed==0) throw new BizException(ResultCode.FORBIDDEN,"无权查看该业务存证任务");
        }
        return t;
    }
    public Map<String,Object> list(int current,int size){
        size=Math.max(1,Math.min(100,size));current=Math.max(1,current);
        Map<String,Object> page=new LinkedHashMap<>();page.put("records",db.queryForList("SELECT task_id AS taskId,event_key AS eventKey,biz_type AS bizType,biz_id AS bizId,project_id AS projectId,status,chain_source AS chainSource,retry_count AS retryCount,error_msg AS errorMsg,tx_id AS txId,duration_ms AS durationMs FROM evidence_task ORDER BY task_id DESC LIMIT ? OFFSET ?",size,(current-1)*size));page.put("total",db.queryForObject("SELECT COUNT(*) FROM evidence_task",Long.class));return page;
    }
    public Map<String,Object> retry(Long id){
        Map<String,Object> t=detail(id);
        if(!"SUBMITTED".equals(t.get("status"))) db.update("UPDATE evidence_task SET status='PENDING',retry_count=0,next_retry_time=NULL,error_msg=NULL,update_time=NOW() WHERE task_id=? AND status<>'SUBMITTED'",id);
        return detail(id);
    }
}
