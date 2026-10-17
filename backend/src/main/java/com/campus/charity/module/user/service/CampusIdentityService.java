package com.campus.charity.module.user.service;
import com.campus.charity.common.annotation.OpLog;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.module.file.entity.FileRecord;
import com.campus.charity.module.file.service.FileService;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.validation.constraints.*;
import java.util.*;
@Service
public class CampusIdentityService {
    private final JdbcTemplate db; private final FileService files; private final CampusIdentityPolicy policy;
    public CampusIdentityService(JdbcTemplate db, FileService files, CampusIdentityPolicy policy) {this.db=db;this.files=files;this.policy=policy;}
    @Data public static class Submit {
        @NotBlank @Size(max=64) private String identityNo;
        @Size(max=64) private String realName;
        @Size(max=64) private String college;
        private Long fileId; private Boolean acknowledged;
    }
    public Map<String,Object> config() {
        Map<String,Object> r=new LinkedHashMap<>();r.put("testMode",policy.isTestMode());
        r.put("notice",policy.isTestMode()?"内部测试认证，不代表学校正式实名认证":"校园身份资料由学校人工审核，原件不上链");return r;
    }
    @Transactional @PreAuthorize("hasRole('STUDENT')")
    @OpLog(module="校园认证",value="提交校园身份确认",saveParams=false)
    public Map<String,Object> submit(Submit f) {
        long uid=SecurityUtil.getUserId();Map<String,Object> u=db.queryForMap("SELECT * FROM sys_user WHERE user_id=? AND deleted=0 FOR UPDATE",uid);
        String no=f.getIdentityNo()==null?"":f.getIdentityNo().trim();
        if(no.isEmpty()||no.length()>64||!no.matches("[\\p{L}\\p{N}_-]+")) fail("请填写有效学号（字母、数字、下划线或短横线，最多64位）");
        if(policy.isTestMode()) {
            if(!Boolean.TRUE.equals(f.getAcknowledged())) fail("请确认内部测试认证声明");
            String name=f.getRealName()==null?"":f.getRealName().trim(),college=f.getCollege()==null?"":f.getCollege().trim();
            if(name.isEmpty()||name.length()>64||college.isEmpty()||college.length()>64) fail("请填写姓名和学校/院系信息（最多64字）");
            // 正式认证不被测试申请降级；重复及并发请求保持同一有效记录。
            if(((Number)u.get("auth_status")).intValue()==2&&((Number)u.get("identity_test_only")).intValue()==0)return config();
            boolean same=Objects.equals(no,u.get("identity_no"))&&Objects.equals(name,u.get("real_name"))&&Objects.equals(college,u.get("college"));
            if(same&&((Number)u.get("auth_status")).intValue()==2&&((Number)u.get("identity_test_only")).intValue()==1)return config();
            db.update("UPDATE identity_review SET status=4 WHERE user_id=? AND status=1",uid);
            db.update("INSERT INTO identity_review(user_id,identity_no,file_id,status,verification_mode,opinion,review_time,create_time) VALUES(?,?,NULL,2,'TEST','内部测试认证，不代表学校正式实名认证',NOW(),NOW())",uid,no);
            db.update("UPDATE sys_user SET identity_no=?,real_name=?,college=?,auth_status=2,identity_test_only=1,update_time=NOW() WHERE user_id=?",no,name,college,uid);
        }else{
            if(db.queryForObject("SELECT COUNT(*) FROM identity_review WHERE user_id=? AND status=1",Integer.class,uid)>0)fail("已有校园身份申请待审核");
            if(f.getFileId()==null)fail("请上传本人的校园身份证明，等待学校审核");
            FileRecord file=files.getById(f.getFileId());
            if(!Objects.equals(uid,file.getUploaderId())||!"IDENTITY".equals(file.getBizType()))throw new BizException(ResultCode.FORBIDDEN,"请上传本人的校园身份证明");
            db.update("INSERT INTO identity_review(user_id,identity_no,file_id,status,verification_mode,create_time) VALUES(?,?,?,1,'MANUAL',NOW())",uid,no,f.getFileId());
            db.update("UPDATE sys_user SET identity_no=?,auth_status=1,identity_test_only=0,update_time=NOW() WHERE user_id=?",no,uid);
        }
        return config();
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    @OpLog(module="校园认证",value="学校审核校园身份",saveParams=false)
    public void audit(Long id,boolean pass,String opinion) {
        List<Map<String,Object>> records=db.queryForList("SELECT user_id FROM identity_review WHERE review_id=?",id);
        if(records.isEmpty())fail("认证申请不存在");Object uid=records.get(0).get("user_id");
        db.queryForList("SELECT user_id FROM sys_user WHERE user_id=? FOR UPDATE",uid);
        Map<String,Object> r=db.queryForMap("SELECT * FROM identity_review WHERE review_id=? FOR UPDATE",id);
        if(((Number)r.get("status")).intValue()!=1||!"MANUAL".equals(r.get("verification_mode")))fail("该申请已处理，不能再审核");
        if(!pass&&(opinion==null||opinion.trim().isEmpty()))fail("驳回必须填写意见");
        db.update("UPDATE identity_review SET status=?,opinion=?,reviewer_id=?,review_time=NOW() WHERE review_id=?",pass?2:3,opinion,SecurityUtil.getUserId(),id);
        db.update("UPDATE sys_user SET auth_status=?,identity_test_only=0,update_time=NOW() WHERE user_id=?",pass?2:3,uid);
    }
    private void fail(String message){throw new BizException(ResultCode.BAD_REQUEST,message);}
}
