package com.campus.charity.module.project.service;

import com.campus.charity.common.api.PageResult;
import com.campus.charity.module.project.dto.ProjectDtos;
import com.campus.charity.module.project.entity.Project;

import java.util.List;
import java.util.Map;

/**
 * 众筹项目管理服务。
 *
 * <p>覆盖用例：浏览项目（include 查看区块链存证）、查看详情（include 收藏）、社团发布项目
 * （include 设置筹款目标）、编辑项目（include 更新项目进度）、提交证明材料（extends 上链）、
 * 查看审核状态（include 查看审核意见）、查看捐款记录（include 查看资金流向）、
 * 管理员审核（include 合规审查）、下架违规项目、校内公示。</p>
 */
public interface ProjectService {

    /** 捐赠者：分页浏览已上架项目（列表自带链上存证信息） */
    PageResult<ProjectDtos.ProjectVO> pageOnline(ProjectDtos.ProjectQuery query);

    /** 查看项目详情（include 收藏项目，返回是否已收藏） */
    ProjectDtos.ProjectDetailVO detail(Long projectId);

    Project getById(Long projectId);

    /** 社团：发布项目（include 设置筹款目标） */
    Long create(ProjectDtos.ProjectForm form);

    /** 社团：编辑项目（仅未审核 / 驳回状态可编辑，include 更新项目进度） */
    void update(ProjectDtos.ProjectForm form);

    /** 社团：提交项目进入审核 */
    void submit(Long projectId);

    /** 社团：查看本社团全部项目 */
    PageResult<ProjectDtos.ProjectVO> pageMyProjects(ProjectDtos.ProjectQuery query);

    /** 社团：查看项目审核状态 + 审核意见 */
    List<Map<String, Object>> auditHistory(Long projectId);

    /** 管理员：分页查询全部项目 */
    PageResult<ProjectDtos.ProjectVO> pageAll(ProjectDtos.ProjectQuery query);

    /** 管理员：审核项目（include 项目合规审查），通过后项目上架并将摘要哈希上链 */
    void audit(ProjectDtos.ProjectAuditForm form);

    /** 管理员：下架违规项目（extends），处置记录上链存证 */
    void offline(ProjectDtos.OfflineForm form);

    /** 管理员：重新上架项目 */
    void online(Long projectId);

    /** 管理员/社团：项目结项 */
    void finish(Long projectId);
    void auditFinish(Long projectId, String result, String opinion);

    /** 收藏 / 取消收藏（include） */
    boolean toggleFavorite(Long projectId);

    /** 我的收藏 */
    PageResult<ProjectDtos.ProjectVO> pageMyFavorites(ProjectDtos.ProjectQuery query);

    /** 分享项目（extends） */
    void share(Long projectId, String channel);

    /** 评价项目 */
    Long review(ProjectDtos.ReviewForm form);

    /** 项目评价列表 */
    PageResult<Map<String, Object>> pageReviews(Long projectId, long current, long size);

    /** 社团 / 管理员回复评价 */
    void replyReview(ProjectDtos.ReviewReplyForm form);

    /** 提交项目 / 帮扶证明材料（extends 材料哈希上链） */
    Long uploadMaterial(ProjectDtos.MaterialForm form);

    /** 项目材料列表 */
    List<Map<String, Object>> listMaterials(Long projectId);

    /** 新增项目进度（include 更新项目进度） */
    Long addProgress(ProjectDtos.ProgressForm form);

    /** 项目进度列表 */
    List<Map<String, Object>> listProgress(Long projectId);

    /** 项目捐赠明细（社团：查看捐款记录） */
    List<Map<String, Object>> donorList(Long projectId, int limit);

    /** 资金流向（捐赠者：查看资金流向；监管：资金链追踪） */
    Map<String, Object> fundFlow(Long projectId);

    /** 项目平台统计 */
    Map<String, Object> statistics();

    /** 项目审核 / 状态变更记录 */
    List<Map<String, Object>> statusHistory(Long projectId);
}
