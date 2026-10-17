package com.campus.charity.module.user.controller;
import com.campus.charity.common.api.R;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.module.user.service.CampusIdentityService;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
@RestController
public class IdentityController {
    private final JdbcTemplate db; private final CampusIdentityService identities;
    public IdentityController(JdbcTemplate db,CampusIdentityService identities){this.db=db;this.identities=identities;}
    @Data public static class Audit {@NotNull private Boolean pass;@Size(max=1000)private String opinion;}
    @GetMapping("/api/user/identity/config") @PreAuthorize("isAuthenticated()")
    public R<?> config(){return R.ok(identities.config());}
    @PostMapping("/api/user/identity") @PreAuthorize("hasRole('STUDENT')")
    public R<?> submit(@Valid @RequestBody CampusIdentityService.Submit f){return R.ok("校园身份状态已更新",identities.submit(f));}
    @GetMapping("/api/user/identity") @PreAuthorize("hasRole('STUDENT')")
    public R<?> mine(){return R.ok(db.queryForList("SELECT review_id AS reviewId,identity_no AS identityNo,verification_mode AS verificationMode,status,opinion,review_time AS reviewTime,create_time AS createTime FROM identity_review WHERE user_id=? ORDER BY review_id DESC",SecurityUtil.getUserId()));}
    @GetMapping("/api/admin/identities") @PreAuthorize("hasRole('ADMIN')")
    public R<?> list(@RequestParam(defaultValue="1")int current,@RequestParam(defaultValue="20")int size){
        size=Math.max(1,Math.min(size,100));return R.ok(db.queryForList("SELECT r.review_id AS reviewId,r.user_id AS userId,r.identity_no AS identityNo,r.file_id AS fileId,r.verification_mode AS verificationMode,r.status,r.opinion,u.real_name AS realName FROM identity_review r JOIN sys_user u ON u.user_id=r.user_id ORDER BY r.review_id DESC LIMIT ? OFFSET ?",size,(Math.max(1,current)-1)*size));
    }
    @PostMapping("/api/admin/identities/{id}/audit") @PreAuthorize("hasRole('ADMIN')")
    public R<?> audit(@PathVariable Long id,@Valid @RequestBody Audit f){identities.audit(id,f.getPass(),f.getOpinion());return R.ok("身份审核完成",null);}
}
