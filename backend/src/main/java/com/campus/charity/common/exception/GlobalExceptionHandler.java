package com.campus.charity.common.exception;

import com.campus.charity.common.api.R;
import com.campus.charity.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理：统一返回 {@link R}，避免异常堆栈直接暴露给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class)
    public R<Void> missingRecord(Exception e) {return R.fail(ResultCode.NOT_FOUND,"业务记录不存在");}
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public R<Void> duplicateRecord(Exception e) {return R.build(409,"业务键已被使用，请刷新核对原请求结果",null);}

    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.build(e.getCode(), e.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("越权访问被拦截: {}", e.getMessage());
        return R.fail(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValidException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return R.fail(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        return R.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        return R.fail(ResultCode.FILE_ERROR, "上传文件超过大小限制");
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail(ResultCode.BIZ_ERROR, "系统繁忙，请稍后重试");
    }
}
