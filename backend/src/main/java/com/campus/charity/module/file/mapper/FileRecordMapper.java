package com.campus.charity.module.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.charity.module.file.entity.FileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileRecordMapper extends BaseMapper<FileRecord> {

    /** 根据文件关联表解析项目，供原始文件下载的数据权限校验使用。 */
    @Select("SELECT COALESCE(pm.project_id, fu.project_id, p.project_id) " +
            "FROM file_record fr " +
            "LEFT JOIN project_material pm ON pm.file_id = fr.file_id AND pm.deleted = 0 " +
            "LEFT JOIN fund_usage fu ON fu.file_id = fr.file_id AND fu.deleted = 0 " +
            "LEFT JOIN project p ON fr.biz_type = 'PROJECT_COVER' " +
            "AND p.project_id = CAST(fr.biz_id AS UNSIGNED) AND p.deleted = 0 " +
            "WHERE fr.file_id = #{fileId} AND fr.deleted = 0")
    Long findProjectId(@Param("fileId") Long fileId);

    /** 返回项目材料类型，学生下载时用于拦截帮扶对象名单等敏感原件。 */
    @Select("SELECT pm.material_type FROM project_material pm " +
            "WHERE pm.file_id = #{fileId} AND pm.deleted = 0 LIMIT 1")
    String findMaterialType(@Param("fileId") Long fileId);
    @Select("SELECT applicant_id FROM refund_application WHERE voucher_file_id=#{fileId} AND status='REFUNDED' LIMIT 1")
    Long findRefundApplicant(@Param("fileId") Long fileId);
}
