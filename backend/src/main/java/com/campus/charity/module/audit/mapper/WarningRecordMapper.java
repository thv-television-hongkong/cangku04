package com.campus.charity.module.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.audit.entity.WarningRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface WarningRecordMapper extends BaseMapper<WarningRecord> {

    @Select("SELECT warning_level AS name, COUNT(*) AS value FROM warning_record GROUP BY warning_level")
    List<Map<String, Object>> countByLevel();

    @Select("SELECT COUNT(*) FROM warning_record WHERE handle_status = 0")
    long countUnhandled();
}
