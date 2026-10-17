package com.campus.charity.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.admin.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {

    @Select("SELECT module AS name, COUNT(*) AS value FROM sys_operation_log GROUP BY module ORDER BY value DESC")
    List<Map<String, Object>> countByModule();

    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS name, COUNT(*) AS value FROM sys_operation_log " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') ORDER BY name DESC LIMIT 15")
    List<Map<String, Object>> countByDay();
}
