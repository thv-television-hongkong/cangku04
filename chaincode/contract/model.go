package contract

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

// ---------------------------------------------------------------------------------------
// 链上数据结构
// 说明：链上仅保存哈希摘要与非敏感业务字段（金额、状态、业务编号、时间、操作人角色）。
//      业务原文、原始文件、帮扶对象姓名、捐赠人姓名等一律不上链。
// ---------------------------------------------------------------------------------------

// 链上键前缀（采用复合键 + 前缀范围查询，兼容 LevelDB 与 CouchDB，无需富查询）
const (
	PrefixEvidence = "EV"        // EV#<bizType>#<bizId>           业务当前存证
	PrefixTxIndex  = "TXIDX"     // TXIDX#<txId>                   交易ID 索引
	PrefixBlockIdx = "BLKIDX"    // BLKIDX#<blockHeight>           区块高度索引（后端回填真实高度）
	PrefixProjIdx  = "PRJIDX"    // PRJIDX#<projectId>#<chainSeq>  项目全流程索引
	PrefixSeqIdx   = "SEQIDX"    // SEQIDX#<chainSeq>                全局序号索引（链式完整性校验）
	PrefixSeq      = "CHAINSEQ"  // 全局存证序号计数器
	PrefixHead     = "CHAINHEAD" // 链式前序哈希头（形成不可篡改的存证链）
	PrefixStats    = "STATS"     // 按业务类型的计数
)

// 业务类型（与后端 BizType 常量一一对应）
const (
	BizProject         = "PROJECT"
	BizDonation        = "DONATION"
	BizFundUsage       = "FUND_USAGE"
	BizMaterial        = "MATERIAL"
	BizWithdrawal      = "WITHDRAWAL"
	BizProjectProgress = "PROJECT_PROGRESS"
	BizAuditReport     = "AUDIT_REPORT"
	BizProjectStatus   = "PROJECT_STATUS"
	BizRefund          = "REFUND"
)

// Evidence 链上存证记录
type Evidence struct {
	EventKey      string            `json:"eventKey"`
	Key           string            `json:"key"`           // 链上主键 EV#<bizType>#<bizId>
	BizType       string            `json:"bizType"`       // 业务类型
	BizId         string            `json:"bizId"`         // 关联业务ID
	ProjectId     string            `json:"projectId"`     // 关联项目ID（便于按项目溯源）
	DataHash      string            `json:"dataHash"`      // 存证数据哈希（SHA-256）
	FileHash      string            `json:"fileHash"`      // 原始文件哈希（原始文件存业务服务器）
	Amount        string            `json:"amount"`        // 金额（非隐私，可用于资金链追踪）
	AmountHash    string            `json:"amountHash"`    // 金额哈希（可选）
	DonorHash     string            `json:"donorHash"`     // 捐赠人标识哈希（不上链明文用户ID）
	Operator      string            `json:"operator"`      // 操作人标识（账号或编号，不含姓名）
	OperatorRole  string            `json:"operatorRole"`  // 操作人角色
	Status        string            `json:"status"`        // 业务状态（如 ONLINE / OFFLINE）
	Remark        string            `json:"remark"`        // 非敏感说明
	Extra         map[string]string `json:"extra"`         // 非敏感扩展字段
	TxId          string            `json:"txId"`          // 交易ID
	ChainSeq      int64             `json:"chainSeq"`      // 链上存证序号（单调递增）
	BlockHeight   int64             `json:"blockHeight"`   // 区块高度（由后端索引交易回填）
	Timestamp     string            `json:"timestamp"`     // 链上交易时间（RFC3339）
	PrevChainHash string            `json:"prevChainHash"` // 前一条存证的链哈希
	ChainHash     string            `json:"chainHash"`     // 本条链哈希
	Version       string            `json:"version"`       // 链码版本
}

// EvidenceInput 存证输入（链码内部使用，便于统一校验与落库）
type EvidenceInput struct {
	EventKey     string
	BizType      string
	BizId        string
	ProjectId    string
	DataHash     string
	FileHash     string
	Amount       string
	AmountHash   string
	DonorHash    string
	Operator     string
	OperatorRole string
	Status       string
	Remark       string
	Extra        map[string]string
}

// ChainResult 链码返回给后端 / 前端的存证凭证
// 需求：返回交易 ID、区块高度作为存证凭证
type ChainResult struct {
	TxId        string `json:"txId"`
	BizType     string `json:"bizType"`
	BizId       string `json:"bizId"`
	ProjectId   string `json:"projectId"`
	DataHash    string `json:"dataHash"`
	ChainSeq    int64  `json:"chainSeq"`
	BlockHeight int64  `json:"blockHeight"`
	Timestamp   string `json:"timestamp"`
	ChainHash   string `json:"chainHash"`
	EvidenceKey string `json:"evidenceKey"`
	EvidenceNo  string `json:"evidenceNo"`
	Status      string `json:"status"`
}

// VerifyResult 哈希校验结果
type VerifyResult struct {
	Matched      bool   `json:"matched"`
	BizType      string `json:"bizType"`
	BizId        string `json:"bizId"`
	ProvidedHash string `json:"providedHash"`
	StoredHash   string `json:"storedHash"`
	TxId         string `json:"txId"`
	BlockHeight  int64  `json:"blockHeight"`
	ChainTime    string `json:"chainTime"`
	ChainSeq     int64  `json:"chainSeq"`
	Message      string `json:"message"`
}

// BlockIndex 区块高度索引
type BlockIndex struct {
	BlockHeight int64    `json:"blockHeight"`
	Keys        []string `json:"keys"`
	UpdateTime  string   `json:"updateTime"`
}

// ChainStats 链上存证统计（监管方导出 / 审计概览）
type ChainStats struct {
	TotalEvidence int64            `json:"totalEvidence"`
	ByBizType     map[string]int64 `json:"byBizType"`
	ProjectCount  int64            `json:"projectCount"`
	LatestSeq     int64            `json:"latestSeq"`
	LatestHash    string           `json:"latestHash"`
	ChaincodeVer  string           `json:"chaincodeVersion"`
}

// PagedEvidence 分页结果（导出存证数据）
type PagedEvidence struct {
	Records      []*Evidence `json:"records"`
	Bookmark     string      `json:"bookmark"`
	FetchedCount int32       `json:"fetchedCount"`
}

// ---------------------------------------------------------------------------------------
// 工具函数
// ---------------------------------------------------------------------------------------

// EvidenceKey 生成存证主键
func EvidenceKey(bizType, bizId string) string {
	return PrefixEvidence + "#" + strings.ToUpper(strings.TrimSpace(bizType)) + "#" + strings.TrimSpace(bizId)
}

// TxIndexKey 生成交易ID索引键
func TxIndexKey(txId string) string {
	return PrefixTxIndex + "#" + txId
}

// BlockIndexKey 生成区块高度索引键
func BlockIndexKey(blockHeight int64) string {
	return PrefixBlockIdx + "#" + strconv.FormatInt(blockHeight, 10)
}

// ProjectIndexKey 生成项目溯源索引键
func ProjectIndexKey(projectId string, seq int64) string {
	return PrefixProjIdx + "#" + projectId + "#" + leftPad(strconv.FormatInt(seq, 10), 20)
}

// LeftPadSeq 序号左补零，保证复合键按字典序即按时间序排列
func LeftPadSeq(seq int64) string {
	return leftPad(strconv.FormatInt(seq, 10), 20)
}

func leftPad(s string, width int) string {
	if len(s) >= width {
		return s
	}
	return strings.Repeat("0", width-len(s)) + s
}

// ToJSON 序列化
func ToJSON(v interface{}) ([]byte, error) {
	return json.Marshal(v)
}

// FromJSON 反序列化
func FromJSON(data []byte, v interface{}) error {
	return json.Unmarshal(data, v)
}

// Atos 安全字符串转 int64
func Atos(s string) int64 {
	v, err := strconv.ParseInt(strings.TrimSpace(s), 10, 64)
	if err != nil {
		return 0
	}
	return v
}

// BuildEvidenceNo 生成链上存证编号：EV + 序号
func BuildEvidenceNo(seq int64) string {
	return fmt.Sprintf("EV%s", leftPad(strconv.FormatInt(seq, 10), 10))
}
