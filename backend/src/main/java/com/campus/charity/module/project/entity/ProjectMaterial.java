package com.campus.charity.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目 / 帮扶证明材料（受助者用例：提交项目证明材料 -> 材料哈希上链）。
 */
@Data
@TableName("project_material")
public class ProjectMaterial implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long materialId;
    private Long projectId;
    private Long clubId;
    /** PROJECT_PLAN / PROOF / BENEFICIARY / OTHER */
    private String materialType;
    private String materialName;
    private String remark;
    private Long fileId;
    private String fileName;
    private String fileUrl;
    private String fileHash;
    private Long fileSize;
    /** 0未上链 1已上链 */
    private Integer onChain;
    private LocalDateTime uploadTime;
    private String dataHash;
    private String txId;
    private Long blockHeight;
    private Long evidenceId;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
