package com.campus.charity.module.donation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.donation.entity.Donation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface DonationMapper extends BaseMapper<Donation> {

    @Select("SELECT IFNULL(SUM(amount), 0) FROM donation WHERE project_id = #{projectId} AND status = 'CONFIRMED' AND confirmation_id IS NOT NULL AND refund_status <> 'REFUNDED' AND deleted = 0")
    BigDecimal sumByProject(@Param("projectId") Long projectId);

    @Select("SELECT IFNULL(SUM(amount), 0) FROM donation WHERE donor_id = #{donorId} AND status = 'CONFIRMED' AND confirmation_id IS NOT NULL AND refund_status <> 'REFUNDED' AND deleted = 0")
    BigDecimal sumByDonor(@Param("donorId") Long donorId);

    @Select("SELECT IFNULL(SUM(amount), 0) FROM donation WHERE deleted = 0 AND status = 'CONFIRMED' AND confirmation_id IS NOT NULL AND refund_status <> 'REFUNDED'")
    BigDecimal sumAll();

    @Select("SELECT COUNT(*) FROM donation WHERE deleted = 0 AND status = 'CONFIRMED' AND confirmation_id IS NOT NULL AND refund_status <> 'REFUNDED'")
    long countAll();

    /** 近 N 天捐赠趋势（统计图表） */
    @Select("SELECT DATE_FORMAT(donate_time, '%Y-%m-%d') AS name, IFNULL(SUM(amount),0) AS value, COUNT(*) AS count " +
            "FROM donation WHERE deleted = 0 AND status = 'CONFIRMED' AND confirmation_id IS NOT NULL AND refund_status <> 'REFUNDED' AND donate_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE_FORMAT(donate_time, '%Y-%m-%d') ORDER BY name")
    List<Map<String, Object>> selectTrend(@Param("days") int days);

    /** 单日累计捐赠（预警规则：DAILY_TOTAL） */
    @Select("SELECT IFNULL(SUM(amount),0) FROM donation WHERE donor_id = #{donorId} AND deleted = 0 " +
            "AND status = 'CONFIRMED' AND confirmation_id IS NOT NULL AND refund_status <> 'REFUNDED' AND DATE(donate_time) = DATE(#{time})")
    BigDecimal sumDailyByDonor(@Param("donorId") Long donorId, @Param("time") java.time.LocalDateTime time);

    /** 时间窗口内捐赠次数（预警规则：FREQUENCY） */
    @Select("SELECT COUNT(*) FROM donation WHERE donor_id = #{donorId} AND deleted = 0 " +
            "AND donate_time >= #{from}")
    long countByDonorSince(@Param("donorId") Long donorId, @Param("from") java.time.LocalDateTime from);

    /** 项目捐赠人排行（捐赠明细公开展示，捐赠人名称由服务层脱敏） */
    @Select("SELECT d.donor_id AS donorId, u.real_name AS donorName, d.anonymous AS anonymous, " +
            "SUM(d.amount) AS total, COUNT(*) AS times, MAX(d.donate_time) AS lastTime " +
            "FROM donation d JOIN sys_user u ON u.user_id = d.donor_id " +
            "WHERE d.project_id = #{projectId} AND d.deleted = 0 AND d.status = 'CONFIRMED' AND confirmation_id IS NOT NULL AND refund_status <> 'REFUNDED' " +
            "GROUP BY d.donor_id, u.real_name, d.anonymous ORDER BY total DESC LIMIT #{limit}")
    List<Map<String, Object>> selectDonorRank(@Param("projectId") Long projectId, @Param("limit") int limit);
}
