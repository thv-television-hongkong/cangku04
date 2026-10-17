package com.campus.charity.module.fund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.fund.entity.FundWithdrawal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface FundWithdrawalMapper extends BaseMapper<FundWithdrawal> {

    @Select("SELECT IFNULL(SUM(amount),0) FROM fund_withdrawal WHERE project_id = #{projectId} " +
            "AND status IN (1,3) AND deleted = 0")
    BigDecimal sumWithdrawnByProject(@Param("projectId") Long projectId);
}
