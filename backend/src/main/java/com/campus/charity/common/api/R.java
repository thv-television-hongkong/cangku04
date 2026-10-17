package com.campus.charity.common.api;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果。
 *
 * @param <T> 业务数据类型
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，200 表示成功 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 业务数据 */
    private T data;
    /** 服务器时间戳 */
    private long timestamp = System.currentTimeMillis();

    public static <T> R<T> ok() {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> R<T> ok(T data) {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> R<T> ok(String message, T data) {
        return build(ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> R<T> fail(String message) {
        return build(ResultCode.BIZ_ERROR.getCode(), message, null);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return build(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return build(resultCode.getCode(), message, null);
    }

    public static <T> R<T> build(int code, String message, T data) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
