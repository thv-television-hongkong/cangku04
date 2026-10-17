package com.campus.charity.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目收藏（捐赠者用例：查看项目详情 include 收藏项目）。
 */
@Data
@TableName("project_favorite")
public class ProjectFavorite implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long favoriteId;
    private Long projectId;
    private Long userId;
    private LocalDateTime createTime;
}
