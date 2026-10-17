package com.campus.charity.common.aop;

import cn.hutool.core.util.StrUtil;
import com.campus.charity.common.annotation.OpLog;
import com.campus.charity.common.constant.CommonConst;
import com.campus.charity.common.util.WebUtil;
import com.campus.charity.module.admin.entity.SysOperationLog;
import com.campus.charity.module.admin.mapper.SysOperationLogMapper;
import com.campus.charity.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作日志切面：记录全部关键业务操作，实现操作审计追踪。
 *
 * <p>安全：密码等敏感参数在入库前被过滤，不记录明文密码。</p>
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private static final String[] SENSITIVE_KEYS = {"password", "oldPassword", "newPassword", "confirmPassword", "idCardNo"};

    private final SysOperationLogMapper logMapper;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(SysOperationLogMapper logMapper, ObjectMapper objectMapper) {
        this.logMapper = logMapper;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        String errorMsg = null;
        String status = CommonConst.LOG_SUCCESS;
        Object result;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            status = CommonConst.LOG_FAIL;
            errorMsg = e.getClass().getSimpleName();
            throw e;
        } finally {
            try {
                recordLog(joinPoint, opLog, status, errorMsg, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("操作日志记录失败: {}", e.getMessage());
            }
        }
    }

    @Async
    protected void recordLog(ProceedingJoinPoint joinPoint, OpLog opLog, String status,
                             String errorMsg, long duration) {
        SysOperationLog entity = new SysOperationLog();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            entity.setUserId(loginUser.getUserId());
            entity.setAccount("USER#"+loginUser.getUserId());
            entity.setRealName(com.campus.charity.common.util.MaskUtil.maskName(loginUser.getRealName()));
            entity.setRole(loginUser.getRole());
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        entity.setModule(opLog.module());
        entity.setOperation(opLog.value());
        entity.setMethod(signature.getDeclaringTypeName() + "." + signature.getName());
        HttpServletRequest request = WebUtil.getRequest();
        if (request != null) {
            entity.setRequestUri(request.getRequestURI());
            entity.setRequestMethod(request.getMethod());
        }
        entity.setIp(WebUtil.getClientIp());
        entity.setUserAgent(WebUtil.getUserAgent());
        entity.setResultStatus(status);
        entity.setErrorMsg(StrUtil.maxLength(errorMsg, 990));
        entity.setDurationMs(duration);
        entity.setCreateTime(LocalDateTime.now());
        if (opLog.saveParams()) {
            entity.setRequestParams(StrUtil.maxLength(buildParams(joinPoint.getArgs()), 1900));
        }
        logMapper.insert(entity);
    }

    private String buildParams(Object[] args) {
        try {
            // 只允许业务引用进入日志；密码、验证码、JWT、身份信息和材料正文均不记录。
            com.fasterxml.jackson.databind.JsonNode tree=objectMapper.valueToTree(Arrays.asList(args));
            java.util.Map<String,Object> refs=new java.util.LinkedHashMap<>();
            collectReferences(tree,refs);
            return objectMapper.writeValueAsString(refs);
        } catch (Exception e) {
            return "参数序列化失败";
        }
    }

    private void collectReferences(com.fasterxml.jackson.databind.JsonNode node, java.util.Map<String,Object> refs) {
        if(node.isArray()) {node.forEach(n->collectReferences(n,refs));return;}
        if(!node.isObject()) return;
        java.util.Set<String> allowed=new java.util.HashSet<>(Arrays.asList("projectId","donationId","orderId","refundId","taskId","fileId","result","status","role"));
        node.fields().forEachRemaining(e->{if(allowed.contains(e.getKey())&&e.getValue().isValueNode())refs.put(e.getKey(),e.getValue().asText());});
    }
}
