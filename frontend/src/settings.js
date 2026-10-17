module.exports = {
  title: '校园公益众筹存证溯源系统',
  /** 系统定位提示：仅校内使用，不面向社会公开募捐 */
  legalNotice: '本系统仅面向校园内部公益众筹，不面向社会公开募捐；链上仅保存文件哈希摘要，原始文件与个人隐私数据保存在学校业务服务器。',
  roles: {
    STUDENT: '普通学生',
    CLUB: '公益社团',
    ADMIN: '学校管理 · 审核财务',
    AUDITOR: '学校管理 · 监管审计'
  },
  /** 链上存证业务类型（与后端 BizTypeConst 一致） */
  bizTypes: [
    { value: 'PROJECT', label: '众筹项目' },
    { value: 'DONATION', label: '捐赠记录' },
    { value: 'REFUND', label: '退款完成记录' },
    { value: 'FUND_USAGE', label: '资金使用凭证' },
    { value: 'MATERIAL', label: '帮扶/项目材料' },
    { value: 'WITHDRAWAL', label: '资金提取' },
    { value: 'PROJECT_PROGRESS', label: '项目进度' },
    { value: 'PROJECT_STATUS', label: '项目状态变更' },
    { value: 'AUDIT_REPORT', label: '审计报告' }
  ]
}
