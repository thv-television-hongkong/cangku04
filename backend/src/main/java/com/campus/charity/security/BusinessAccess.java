package com.campus.charity.security;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.SecurityUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class BusinessAccess {
    private final JdbcTemplate db;
    public BusinessAccess(JdbcTemplate db){this.db=db;}
    public boolean project(Long id){
        if(SecurityUtil.isAdmin()||SecurityUtil.isAuditor())return true;
        if(id==null)return false;
        Long uid=SecurityUtil.getUserId();
        if(SecurityUtil.isClub())return db.queryForObject("SELECT COUNT(*) FROM project WHERE project_id=? AND club_id=? AND deleted=0",Integer.class,id,uid)>0;
        return db.queryForObject("SELECT COUNT(*) FROM project p WHERE p.project_id=? AND p.deleted=0 AND (p.status IN ('ONLINE','FINISH_PENDING','FINISHED','SETTLED') OR EXISTS(SELECT 1 FROM donation d WHERE d.project_id=p.project_id AND d.donor_id=? AND d.deleted=0))",Integer.class,id,uid)>0;
    }
    public void requireProject(Long id){if(!project(id))throw new BizException(ResultCode.FORBIDDEN,"无权访问该项目资料");}
    public boolean evidence(String type,String bizId,Long projectId){
        if(SecurityUtil.isAdmin()||SecurityUtil.isAuditor())return true;
        if(SecurityUtil.isStudent()&&"DONATION".equals(type))return db.queryForObject("SELECT COUNT(*) FROM donation WHERE donation_id=? AND donor_id=? AND deleted=0",Integer.class,bizId,SecurityUtil.getUserId())>0;
        if(SecurityUtil.isStudent()&&"REFUND".equals(type))return db.queryForObject("SELECT COUNT(*) FROM refund_application WHERE refund_id=? AND applicant_id=?",Integer.class,bizId,SecurityUtil.getUserId())>0;
        return project(projectId);
    }
}
