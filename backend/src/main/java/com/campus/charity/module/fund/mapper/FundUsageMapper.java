package com.campus.charity.module.fund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.fund.entity.FundUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface FundUsageMapper extends BaseMapper<FundUsage> {

    @Select("SELECT IFNULL(SUM(amount),0) FROM fund_usage WHERE project_id = #{projectId} AND deleted = 0 AND audit_status = 1")
    BigDecimal sumApprovedByProject(@Param("projectId") Long projectId);

    @Select("SELECT IFNULL(SUM(amount),0) FROM fund_usage WHERE project_id = #{projectId} AND deleted = 0")
    BigDecimal sumByProject(@Param("projectId") Long projectId);

    /** 资金流向明细（捐赠者“查看资金流向” / 监管方“资金链追踪”） */
    @Select("SELECT usage_id AS usageId, usage_no AS usageNo, usage_desc AS usageDesc, amount, " +
            "use_time AS useTime, file_name AS fileName, file_hash AS fileHash, audit_status AS auditStatus, " +
            "tx_id AS txId, block_height AS blockHeight FROM fund_usage " +
            "WHERE project_id = #{projectId} AND deleted = 0 ORDER BY upload_time DESC")
    List<Map<String, Object>> selectFlowByProject(@Param("projectId") Long projectId);
}
