package com.campus.charity.module.evidence;
import com.campus.charity.common.api.R;
import com.campus.charity.module.evidence.service.EvidenceTaskService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class EvidenceTaskController {
    private final EvidenceTaskService service;
    public EvidenceTaskController(EvidenceTaskService service){this.service=service;}
    @GetMapping("/api/evidence/tasks/{id}") public R<?> detail(@PathVariable Long id){return R.ok(service.detail(id));}
    @GetMapping("/api/admin/evidence/tasks") @PreAuthorize("hasRole('ADMIN')") public R<?> list(@RequestParam(defaultValue="1")int current,@RequestParam(defaultValue="20")int size){return R.ok(service.list(current,size));}
    @PostMapping("/api/admin/evidence/tasks/{id}/retry") @PreAuthorize("hasRole('ADMIN')") public R<?> retry(@PathVariable Long id){return R.ok("已安排重试，将先核对原事件避免重复提交",service.retry(id));}
}
