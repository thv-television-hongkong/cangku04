package com.campus.charity.common.exception;

import com.campus.charity.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.BIZ_ERROR.getCode();
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
