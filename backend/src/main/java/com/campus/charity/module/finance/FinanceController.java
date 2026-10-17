package com.campus.charity.module.finance;

import com.campus.charity.common.api.R;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.Map;

@RestController @RequestMapping("/api")
public class FinanceController {
    private final FinanceService service;
    public FinanceController(FinanceService service){this.service=service;}
    @PostMapping("/orders") @PreAuthorize("hasRole('STUDENT')")
    public R<?> create(@Valid @RequestBody FinanceForms.Order f){return R.ok("模拟订单已创建，请选择测试支付结果",service.create(f));}
    @PostMapping("/orders/{id}/mock-pay") @PreAuthorize("hasRole('STUDENT')")
    public R<?> pay(@PathVariable Long id,@Valid @RequestBody FinanceForms.Payment f){return R.ok(service.pay(id,f));}
    @PostMapping("/orders/{id}/resubmit") @PreAuthorize("hasRole('STUDENT')")
    public R<?> resubmit(@PathVariable Long id,@Valid @RequestBody FinanceForms.Resubmit f){return R.ok(service.resubmit(id,f));}
    @GetMapping("/orders/mine") @PreAuthorize("hasRole('STUDENT')")
    public R<?> mine(@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) String status){return R.ok(service.orders(false,current,size,status,null));}
    @GetMapping("/orders/{id}")
    public R<?> detail(@PathVariable Long id){return R.ok(service.orderDetail(id));}
    @GetMapping("/finance/orders") @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public R<?> queue(@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) String status,@RequestParam(required=false) Long projectId){return R.ok(service.orders(true,current,size,status,projectId));}
    @PostMapping("/finance/confirmations") @PreAuthorize("hasRole('ADMIN')")
    public R<?> confirm(@Valid @RequestBody FinanceForms.Confirmation f){return R.ok(service.confirm(f));}
    @PostMapping("/refunds") @PreAuthorize("hasRole('STUDENT')")
    public R<?> refund(@Valid @RequestBody FinanceForms.Refund f){return R.ok(service.applyRefund(f));}
    @GetMapping("/refunds/mine") @PreAuthorize("hasRole('STUDENT')")
    public R<?> refunds(@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) String status){return R.ok(service.refunds(false,current,size,status,null));}
    @GetMapping("/refunds/{id}")
    public R<?> refundDetail(@PathVariable Long id){return R.ok(service.refundDetail(id));}
    @GetMapping("/finance/refunds") @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public R<?> refundQueue(@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int size,@RequestParam(required=false) String status,@RequestParam(required=false) Long projectId){return R.ok(service.refunds(true,current,size,status,projectId));}
    @PostMapping("/finance/refunds/{id}/audit") @PreAuthorize("hasRole('ADMIN')")
    public R<?> audit(@PathVariable Long id,@Valid @RequestBody FinanceForms.Audit f){return R.ok(service.auditRefund(id,f));}
    @PostMapping("/finance/refunds/{id}/complete") @PreAuthorize("hasRole('ADMIN')")
    public R<?> complete(@PathVariable Long id,@Valid @RequestBody FinanceForms.Complete f){return R.ok(service.completeRefund(id,f));}
}
