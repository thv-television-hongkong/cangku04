package com.campus.charity.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.project.entity.ProjectReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface ProjectReviewMapper extends BaseMapper<ProjectReview> {

    @Select("SELECT IFNULL(AVG(rating), 0) AS avgRating, COUNT(*) AS reviewCount FROM project_review " +
            "WHERE project_id = #{projectId} AND deleted = 0 AND status = 1")
    Map<String, Object> selectRatingSummary(@Param("projectId") Long projectId);
}
