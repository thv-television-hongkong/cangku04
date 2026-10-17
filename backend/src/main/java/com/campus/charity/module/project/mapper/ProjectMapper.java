package com.campus.charity.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
    @Select("SELECT * FROM project WHERE project_id=#{id} AND deleted=0 FOR UPDATE")
    Project lockById(@Param("id") Long id);

    /** 按状态统计项目数 */
    @Select("SELECT status AS name, COUNT(*) AS value FROM project WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countByStatus();

    /** 按帮扶类别统计 */
    @Select("SELECT category AS name, COUNT(*) AS value FROM project WHERE deleted = 0 GROUP BY category")
    List<Map<String, Object>> countByCategory();

    /** 平台筹款总额 */
    @Select("SELECT IFNULL(SUM(current_amount), 0) FROM project WHERE deleted = 0")
    BigDecimal sumCurrentAmount();

    /** 平台目标总额 */
    @Select("SELECT IFNULL(SUM(target_amount), 0) FROM project WHERE deleted = 0")
    BigDecimal sumTargetAmount();

    /** 原子累加项目筹款（防止并发捐赠下的金额丢失） */
    @Update("UPDATE project SET current_amount = current_amount + #{amount}, donor_count = donor_count + #{donorDelta}, " +
            "update_time = NOW() WHERE project_id = #{projectId} AND deleted = 0")
    int accumulateAmount(@Param("projectId") Long projectId, @Param("amount") BigDecimal amount,
                         @Param("donorDelta") int donorDelta);

    /** 浏览量 +1 */
    @Update("UPDATE project SET view_count = view_count + 1 WHERE project_id = #{projectId}")
    int increaseViewCount(@Param("projectId") Long projectId);

    /** 收藏量累加 */
    @Update("UPDATE project SET favorite_count = GREATEST(favorite_count + #{delta}, 0) WHERE project_id = #{projectId}")
    int increaseFavoriteCount(@Param("projectId") Long projectId, @Param("delta") int delta);

    /** 分享量 +1 */
    @Update("UPDATE project SET share_count = share_count + 1 WHERE project_id = #{projectId}")
    int increaseShareCount(@Param("projectId") Long projectId);

    /** 项目资金流向（对应视图 v_project_fund_flow） */
    @Select("SELECT * FROM v_project_fund_flow WHERE project_id = #{projectId}")
    Map<String, Object> selectFundFlow(@Param("projectId") Long projectId);
}
