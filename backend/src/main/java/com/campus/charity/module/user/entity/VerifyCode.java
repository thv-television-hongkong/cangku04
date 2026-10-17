package com.campus.charity.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 验证码（注册 / 找回密码）。
 */
@Data
@TableName("verify_code")
public class VerifyCode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long codeId;
    private String target;
    private String code;
    /** REGISTER / RESET_PASSWORD / LOGIN */
    private String bizType;
    private LocalDateTime expireTime;
    private Integer used;
    private String sendIp;
    private LocalDateTime createTime;
}
