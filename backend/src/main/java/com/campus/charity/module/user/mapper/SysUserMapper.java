package com.campus.charity.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 按角色统计用户数（管理员数据统计） */
    @Select("SELECT role AS name, COUNT(*) AS value FROM sys_user WHERE deleted = 0 GROUP BY role")
    List<Map<String, Object>> countByRole();

    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS name, COUNT(*) AS value FROM sys_user " +
            "WHERE deleted = 0 GROUP BY DATE_FORMAT(create_time, '%Y-%m') ORDER BY name")
    List<Map<String, Object>> countByMonth();
}
