package com.campus.charity.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（管理员用例：查看系统日志 include 操作审计追踪）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 业务模块 */
    String module() default "系统";

    /** 操作描述 */
    String value();

    /** 是否记录请求参数 */
    boolean saveParams() default true;
}
