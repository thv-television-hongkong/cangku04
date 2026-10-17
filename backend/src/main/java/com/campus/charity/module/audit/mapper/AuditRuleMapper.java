package com.campus.charity.module.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.audit.entity.AuditRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuditRuleMapper extends BaseMapper<AuditRule> {

    @Update("UPDATE audit_rule SET hit_count = hit_count + 1 WHERE rule_id = #{ruleId}")
    int increaseHitCount(@Param("ruleId") Long ruleId);
}
