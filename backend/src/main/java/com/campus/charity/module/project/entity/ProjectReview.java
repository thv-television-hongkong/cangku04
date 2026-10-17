package com.campus.charity.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目评价（捐赠者用例：评价项目）。
 */
@Data
@TableName("project_review")
public class ProjectReview implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long reviewId;
    private Long projectId;
    private Long userId;
    private Long donationId;
    private Integer rating;
    private String content;
    private Integer anonymous;
    private Integer status;
    private String replyContent;
    private LocalDateTime replyTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
