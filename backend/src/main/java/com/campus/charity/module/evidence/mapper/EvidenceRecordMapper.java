package com.campus.charity.module.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.evidence.entity.EvidenceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface EvidenceRecordMapper extends BaseMapper<EvidenceRecord> {

    @Select("SELECT biz_type AS name, COUNT(*) AS value FROM evidence_record WHERE status = 'SUCCESS' GROUP BY biz_type")
    List<Map<String, Object>> countByBizType();

    @Select("SELECT COUNT(*) FROM evidence_record WHERE status = 'SUCCESS'")
    long countSuccess();

    @Select("SELECT MAX(block_height) FROM evidence_record WHERE status = 'SUCCESS'")
    Long selectMaxBlockHeight();

    @Select("SELECT * FROM v_evidence_detail WHERE project_id = #{projectId} ORDER BY chain_time ASC")
    List<Map<String, Object>> selectDetailByProject(@Param("projectId") Long projectId);

    @Select("SELECT * FROM v_evidence_detail WHERE biz_type = #{bizType} AND biz_id = #{bizId} LIMIT 1")
    Map<String, Object> selectDetailByBiz(@Param("bizType") String bizType, @Param("bizId") String bizId);

    @Select("SELECT * FROM v_evidence_detail WHERE tx_id = #{txId} LIMIT 1")
    Map<String, Object> selectDetailByTxId(@Param("txId") String txId);

    @Select("SELECT * FROM v_evidence_detail WHERE block_height = #{blockHeight}")
    List<Map<String, Object>> selectDetailByBlockHeight(@Param("blockHeight") Long blockHeight);
}
