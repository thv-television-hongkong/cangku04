package com.campus.charity.module.finance;

import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.config.FileProperties;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.common.util.HashUtil;
import com.campus.charity.module.file.entity.FileRecord;
import com.campus.charity.module.file.mapper.FileRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** 模拟支付的服务器回执，明确不代表银行/第三方支付凭证；原文仅存业务服务器。 */
@Service
public class SimulatedPaymentReceiptService {
    private final FileProperties properties;
    private final FileRecordMapper records;
    public SimulatedPaymentReceiptService(FileProperties properties,FileRecordMapper records) {
        this.properties=properties;this.records=records;
    }
    public Long create(Long orderId,String orderNo,Long projectId,Long donorId,BigDecimal amount,String remark) {
        LocalDateTime now=LocalDateTime.now();
        String key=UUID.randomUUID().toString().replace("-","");
        Path root=Paths.get(properties.getUploadPath()).toAbsolutePath().normalize();
        Path target=root.resolve("PAYMENT_VOUCHER").resolve(now.format(DateTimeFormatter.BASIC_ISO_DATE)).resolve(key+".txt");
        String content="校园公益 · 系统自动生成的模拟支付回执\n"
                +"仅用于内部测试，不代表真实支付凭证，未发生实际资金扣款。\n"
                +"订单号："+orderNo+"\n项目ID："+projectId+"\n测试金额："+amount.setScale(2).toPlainString()+" 元\n"
                +"模拟支付结果：成功\n生成时间："+now+"\n补充说明："+(remark==null?"":remark)+"\n";
        byte[] bytes=content.getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
            // 与订单处于同一事务；失败时清理文件，重复支付由订单锁及状态复用原回执。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if(status!=STATUS_COMMITTED) {try{Files.deleteIfExists(target);}catch(IOException ignored){}}
                }
            });
            FileRecord r=new FileRecord();r.setFileNo("AUTO"+key);r.setOriginalName("内部测试支付回执-"+orderNo+".txt");
            r.setStoredName(target.getFileName().toString());r.setFilePath(target.toString());
            r.setFileHash(HashUtil.sha256Hex(bytes));r.setFileSize((long)bytes.length);r.setContentType("text/plain;charset=UTF-8");
            r.setBizType("PAYMENT_VOUCHER");r.setBizId(String.valueOf(orderId));r.setUploaderId(donorId);
            r.setOnChain(0);r.setHashVerified(0);r.setDeleted(0);r.setCreateTime(now);r.setUpdateTime(now);
            records.insert(r);r.setFileUrl(properties.getUrlPrefix()+r.getFileId());records.updateById(r);
            return r.getFileId();
        }catch(IOException ex){
            throw new BizException(ResultCode.FILE_ERROR,"测试支付记录保存失败，请稍后重试或联系管理员检查存储目录");
        }
    }
}
