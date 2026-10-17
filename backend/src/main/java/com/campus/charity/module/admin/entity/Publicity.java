package com.campus.charity.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公益信息公示（管理员用例：项目校内公示）。
 */
@Data
@TableName("publicity")
public class Publicity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long publicityId;
    private String title;
    private String content;
    private Long projectId;
    /** PROJECT / ANNOUNCEMENT / FUND */
    private String publicityType;
    private Integer status;
    private Integer viewCount;
    private Long publishUserId;
    private String publishUserName;
    private LocalDateTime publishTime;
    private LocalDateTime updateTime;
}
