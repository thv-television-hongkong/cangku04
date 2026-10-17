package contract

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/golang/protobuf/ptypes"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// ChaincodeVersion 链码版本（与 contract_record 表中的升级记录对应）
const ChaincodeVersion = "2.0"

// CharityContract 校园公益众筹存证溯源链码
type CharityContract struct {
	contractapi.Contract
}

// AppendEvidence 新版摘要适配接口，只接收编码、状态和摘要，不接收原件或自由文本。
func (c *CharityContract) AppendEvidence(ctx contractapi.TransactionContextInterface,
	eventKey, bizType, bizId, projectId, dataHash, fileHash, status, operatorRole string) (*ChainResult, error) {
	if strings.TrimSpace(eventKey) == "" || len(eventKey) > 180 || strings.ContainsAny(eventKey,"\x00#") {
		return nil, fmt.Errorf("事件键格式无效")
	}
	return c.putEvidence(ctx, EvidenceInput{EventKey:eventKey,BizType:bizType,BizId:bizId,ProjectId:projectId,DataHash:dataHash,FileHash:fileHash,Status:status,Operator:"SCHOOL_SYSTEM",OperatorRole:operatorRole})
}

// CreateRefundEvidence 全额退款完成事件，由财务确认的业务摘要创建。
func (c *CharityContract) CreateRefundEvidence(ctx contractapi.TransactionContextInterface,
	eventKey, refundId, projectId, dataHash, fileHash, status, operatorRole string) (*ChainResult, error) {
	return c.AppendEvidence(ctx,eventKey,BizRefund,refundId,projectId,dataHash,fileHash,status,operatorRole)
}

func (c *CharityContract) QueryByEvent(ctx contractapi.TransactionContextInterface,eventKey string) (*Evidence,error) {
	raw,err:=ctx.GetStub().GetState("EVENT#"+eventKey)
	if err!=nil{return nil,err};if len(raw)==0{return nil,fmt.Errorf("NOT_FOUND: 事件不存在")}
	var e Evidence; if err=json.Unmarshal(raw,&e);err!=nil{return nil,err};return &e,nil
}

// ---------------------------------------------------------------------------------------
// 公共结构
// ---------------------------------------------------------------------------------------

// HistoryRecord 链上历史变更记录
type HistoryRecord struct {
	TxId      string   `json:"txId"`
	Timestamp string   `json:"timestamp"`
	IsDelete  bool     `json:"isDelete"`
	Evidence  Evidence `json:"evidence"`
}

// ProjectTrace 项目全流程溯源结果
type ProjectTrace struct {
	ProjectId       string      `json:"projectId"`
	EvidenceCount   int         `json:"evidenceCount"`
	DonationCount   int         `json:"donationCount"`
	FundUsageCount  int         `json:"fundUsageCount"`
	MaterialCount   int         `json:"materialCount"`
	WithdrawalCount int         `json:"withdrawalCount"`
	DonationTotal   string      `json:"donationTotal"`
	WithdrawalTotal string      `json:"withdrawalTotal"`
	FundUsageTotal  string      `json:"fundUsageTotal"`
	LatestBlock     int64       `json:"latestBlockHeight"`
	LatestChainHash string      `json:"latestChainHash"`
	ChainIntact     bool        `json:"chainIntact"`
	Timeline        []*Evidence `json:"timeline"`
}

// ---------------------------------------------------------------------------------------
// 1. 存证写入（项目 / 捐赠 / 资金凭证 / 帮扶材料 / 资金提取 / 项目进度 / 审计报告）
// ---------------------------------------------------------------------------------------

// CreateProjectEvidence 众筹项目摘要哈希上链存证
// 参数：projectId, dataHash, status, operator, operatorRole
func (c *CharityContract) CreateProjectEvidence(ctx contractapi.TransactionContextInterface,
	projectId, dataHash, status, operator, operatorRole string) (*ChainResult, error) {
	return c.putEvidence(ctx, EvidenceInput{
		BizType:      BizProject,
		BizId:        projectId,
		ProjectId:    projectId,
		DataHash:     dataHash,
		Status:       status,
		Operator:     operator,
		OperatorRole: operatorRole,
	})
}

// CreateDonationEvidence 捐赠记录哈希上链存证
// 参数：donationId, projectId, donorHash, amount, amountHash, dataHash, operator, operatorRole
func (c *CharityContract) CreateDonationEvidence(ctx contractapi.TransactionContextInterface,
	donationId, projectId, donorHash, amount, amountHash, dataHash, operator, operatorRole string) (*ChainResult, error) {
	if err := validateAmount("amount", amount); err != nil {
		return nil, err
	}
	return c.putEvidence(ctx, EvidenceInput{
		BizType:      BizDonation,
		BizId:        donationId,
		ProjectId:    projectId,
		DataHash:     dataHash,
		DonorHash:    donorHash,
		Amount:       normalizeAmount(amount),
		AmountHash:   amountHash,
		Operator:     operator,
		OperatorRole: operatorRole,
	})
}

// CreateFundUsageEvidence 资金使用（报销）凭证哈希上链存证
// 参数：usageId, projectId, amount, fileHash, dataHash, usageStatus, operator, operatorRole
func (c *CharityContract) CreateFundUsageEvidence(ctx contractapi.TransactionContextInterface,
	usageId, projectId, amount, fileHash, dataHash, usageStatus, operator, operatorRole string) (*ChainResult, error) {
	if err := validateAmount("amount", amount); err != nil {
		return nil, err
	}
	return c.putEvidence(ctx, EvidenceInput{
		BizType:      BizFundUsage,
		BizId:        usageId,
		ProjectId:    projectId,
		DataHash:     dataHash,
		FileHash:     fileHash,
		Amount:       normalizeAmount(amount),
		Status:       usageStatus,
		Operator:     operator,
		OperatorRole: operatorRole,
	})
}

// CreateMaterialEvidence 项目 / 帮扶证明材料哈希上链存证
// 参数：materialId, projectId, materialType, fileHash, dataHash, operator, operatorRole
func (c *CharityContract) CreateMaterialEvidence(ctx contractapi.TransactionContextInterface,
	materialId, projectId, materialType, fileHash, dataHash, operator, operatorRole string) (*ChainResult, error) {
	return c.putEvidence(ctx, EvidenceInput{
		BizType:      BizMaterial,
		BizId:        materialId,
		ProjectId:    projectId,
		DataHash:     dataHash,
		FileHash:     fileHash,
		Status:       materialType,
		Operator:     operator,
		OperatorRole: operatorRole,
		Extra:        map[string]string{"materialType": materialType},
	})
}

// CreateWithdrawalEvidence 资金提取记录哈希上链存证
// 参数：withdrawalId, projectId, amount, dataHash, status, operator, operatorRole
func (c *CharityContract) CreateWithdrawalEvidence(ctx contractapi.TransactionContextInterface,
	withdrawalId, projectId, amount, dataHash, status, operator, operatorRole string) (*ChainResult, error) {
	if err := validateAmount("amount", amount); err != nil {
		return nil, err
	}
	return c.putEvidence(ctx, EvidenceInput{
		BizType:      BizWithdrawal,
		BizId:        withdrawalId,
		ProjectId:    projectId,
		DataHash:     dataHash,
		Amount:       normalizeAmount(amount),
		Status:       status,
		Operator:     operator,
		OperatorRole: operatorRole,
	})
}

// CreateProjectProgressEvidence 项目进度哈希上链存证
func (c *CharityContract) CreateProjectProgressEvidence(ctx contractapi.TransactionContextInterface,
	progressId, projectId, fileHash, dataHash, progressPercent, operator, operatorRole string) (*ChainResult, error) {
	return c.putEvidence(ctx, EvidenceInput{
		BizType:      BizProjectProgress,
		BizId:        progressId,
		ProjectId:    projectId,
		DataHash:     dataHash,
		FileHash:     fileHash,
		Status:       progressPercent,
		Operator:     operator,
		OperatorRole: operatorRole,
	})
}

// CreateReportEvidence 审计报告哈希上链存证（监管方生成审计报告后存证）
func (c *CharityContract) CreateReportEvidence(ctx contractapi.TransactionContextInterface,
	reportId, projectId, reportType, fileHash, dataHash, operator, operatorRole string) (*ChainResult, error) {
	return c.putEvidence(ctx, EvidenceInput{
		BizType:      BizAuditReport,
		BizId:        reportId,
		ProjectId:    projectId,
		DataHash:     dataHash,
		FileHash:     fileHash,
		Status:       reportType,
		Operator:     operator,
		OperatorRole: operatorRole,
	})
}

// UpdateProjectStatus 项目状态变更（上架 / 下架 / 结项）存证，形成可溯源的监管处置链路
func (c *CharityContract) UpdateProjectStatus(ctx contractapi.TransactionContextInterface,
	projectId, status, dataHash, reason, operator, operatorRole string) (*ChainResult, error) {
	return c.putEvidence(ctx, EvidenceInput{
		BizType:      BizProjectStatus,
		BizId:        projectId,
		ProjectId:    projectId,
		DataHash:     dataHash,
		Status:       status,
		Remark:       reason,
		Operator:     operator,
		OperatorRole: operatorRole,
	})
}

// putEvidence 统一存证写入：隐私校验 -> 序号分配 -> 链式哈希 -> 落链 -> 建索引
func (c *CharityContract) putEvidence(ctx contractapi.TransactionContextInterface, in EvidenceInput) (*ChainResult, error) {
	in.BizType = NormalizeBizType(in.BizType)
	// Contract API validates response schemas strictly; represent an absent
	// extension map as an empty object rather than JSON null.
	if in.Extra == nil {
		in.Extra = map[string]string{}
	}
	if err := ValidateEvidenceInput(&in); err != nil {
		return nil, err
	}

	txId := ctx.GetStub().GetTxID()
	ts, err := transactionTime(ctx)
	if err != nil {
		return nil, err
	}
	timestamp := ts.Format(time.RFC3339Nano)

	if in.EventKey == "" { in.EventKey = in.BizType+":"+in.BizId+":"+strings.ToLower(in.DataHash) }
	key := "EVENT#"+in.EventKey

	// 幂等处理：相同业务 + 相同哈希重复提交时直接返回已有凭证，避免重复上链
	existingBytes, err := ctx.GetStub().GetState(key)
	if err != nil {
		return nil, fmt.Errorf("读取链上状态失败: %v", err)
	}
	if len(existingBytes) > 0 {
		var exists Evidence
		if err := json.Unmarshal(existingBytes, &exists); err != nil {
			return nil, fmt.Errorf("解析链上存证失败: %v", err)
		}
		if exists.DataHash == in.DataHash {
			return buildChainResult(&exists), nil
		}
		return nil, fmt.Errorf("事件键已存在且摘要不同，禁止覆盖")
	}

	seq, err := c.nextChainSeq(ctx)
	if err != nil {
		return nil, err
	}
	prevHash, err := c.chainHead(ctx)
	if err != nil {
		return nil, err
	}

	evidence := &Evidence{
		EventKey:      in.EventKey,
		Key:           key,
		BizType:       in.BizType,
		BizId:         in.BizId,
		ProjectId:     in.ProjectId,
		DataHash:      in.DataHash,
		FileHash:      in.FileHash,
		Amount:        in.Amount,
		AmountHash:    in.AmountHash,
		DonorHash:     in.DonorHash,
		Operator:      in.Operator,
		OperatorRole:  in.OperatorRole,
		Status:        in.Status,
		Remark:        in.Remark,
		Extra:         in.Extra,
		TxId:          txId,
		ChainSeq:      seq,
		BlockHeight:   0, // 真实区块高度由后端在交易提交后调用 IndexEvidenceBlockHeight 回填
		Timestamp:     timestamp,
		PrevChainHash: prevHash,
		Version:       ChaincodeVersion,
	}
	evidence.ChainHash = computeChainHash(prevHash, key, in.DataHash, txId, timestamp, seq)

	encoded, err := json.Marshal(evidence)
	if err != nil {
		return nil, fmt.Errorf("存证序列化失败: %v", err)
	}
	if err := ctx.GetStub().PutState(key, encoded); err != nil {
		return nil, fmt.Errorf("写入链上存证失败: %v", err)
	}
	// 只更新最新业务指针的副本；交易、项目、全局序号都指向不可变事件。
	if err := ctx.GetStub().PutState(EvidenceKey(in.BizType,in.BizId),encoded);err!=nil{return nil,err}

	// 交易ID索引（支持按交易ID查询存证）
	if err := ctx.GetStub().PutState(TxIndexKey(txId), []byte(key)); err != nil {
		return nil, fmt.Errorf("写入交易ID索引失败: %v", err)
	}
	// 项目索引（支持按项目ID全流程溯源）
	if strings.TrimSpace(in.ProjectId) != "" {
		if err := ctx.GetStub().PutState(ProjectIndexKey(in.ProjectId, seq), []byte(key)); err != nil {
			return nil, fmt.Errorf("写入项目溯源索引失败: %v", err)
		}
	}
	// 全局序号索引（链式完整性校验）
	if err := ctx.GetStub().PutState(PrefixSeqIdx+"#"+LeftPadSeq(seq), []byte(key)); err != nil {
		return nil, fmt.Errorf("写入全局序号索引失败: %v", err)
	}
	// 链头更新
	if err := ctx.GetStub().PutState(PrefixHead, []byte(evidence.ChainHash)); err != nil {
		return nil, fmt.Errorf("更新链头失败: %v", err)
	}
	// 业务类型计数
	if err := c.increaseStat(ctx, in.BizType); err != nil {
		return nil, err
	}

	return buildChainResult(evidence), nil
}

// IndexEvidenceBlockHeight 回填真实区块高度索引。
// Fabric 链码无法获知交易所在区块高度（高度由排序服务在提交后确定），
// 因此后端在交易提交成功后读取 CommitStatus.blockNumber，再以系统身份调用本方法建立
// "区块高度 -> 存证" 索引，从而支持按区块高度查询链上存证。
func (c *CharityContract) IndexEvidenceBlockHeight(ctx contractapi.TransactionContextInterface,
	bizType, bizId, blockHeight, txId string) (*ChainResult, error) {
	height := Atos(blockHeight)
	if height <= 0 {
		return nil, fmt.Errorf("区块高度必须为正整数")
	}
	indexedKey,err:=ctx.GetStub().GetState(TxIndexKey(txId))
	if err!=nil{return nil,err};if len(indexedKey)==0{return nil,fmt.Errorf("交易索引不存在")}
	key := string(indexedKey)
	raw, err := ctx.GetStub().GetState(key)
	if err != nil {
		return nil, fmt.Errorf("读取链上存证失败: %v", err)
	}
	if len(raw) == 0 {
		return nil, fmt.Errorf("链上存证不存在：%s", key)
	}
	var evidence Evidence
	if err := json.Unmarshal(raw, &evidence); err != nil {
		return nil, fmt.Errorf("解析链上存证失败: %v", err)
	}
	if evidence.TxId!=txId || evidence.BizType!=NormalizeBizType(bizType) || evidence.BizId!=bizId{return nil,fmt.Errorf("区块索引与原始交易不一致")}
	if evidence.BlockHeight > 0 && evidence.BlockHeight != height {
		// 已索引过则保持首次结果（区块高度不可变）
		return buildChainResult(&evidence), nil
	}
	evidence.BlockHeight = height
	encoded, err := json.Marshal(&evidence)
	if err != nil {
		return nil, err
	}
	if err := ctx.GetStub().PutState(key, encoded); err != nil {
		return nil, fmt.Errorf("回填区块高度失败: %v", err)
	}
	latest,err:=c.mustGetEvidence(ctx,bizType,bizId)
	if err!=nil{return nil,err};if latest.TxId==txId{if err=ctx.GetStub().PutState(EvidenceKey(bizType,bizId),encoded);err!=nil{return nil,err}}

	idxKey := BlockIndexKey(height)
	rawIdx, err := ctx.GetStub().GetState(idxKey)
	if err != nil {
		return nil, err
	}
	var idx BlockIndex
	if len(rawIdx) > 0 {
		if err := json.Unmarshal(rawIdx, &idx); err != nil {
			return nil, err
		}
	}
	idx.BlockHeight = height
	idx.UpdateTime = evidence.Timestamp
	found := false
	for _, k := range idx.Keys {
		if k == key {
			found = true
			break
		}
	}
	if !found {
		idx.Keys = append(idx.Keys, key)
	}
	encodedIdx, err := json.Marshal(&idx)
	if err != nil {
		return nil, err
	}
	if err := ctx.GetStub().PutState(idxKey, encodedIdx); err != nil {
		return nil, fmt.Errorf("写入区块高度索引失败: %v", err)
	}
	return buildChainResult(&evidence), nil
}

// ---------------------------------------------------------------------------------------
// 2. 多维度查询：业务ID / 交易ID / 区块高度 / 项目ID / 全量分页
// ---------------------------------------------------------------------------------------

// QueryEvidence 按业务类型 + 业务ID 查询存证
func (c *CharityContract) QueryEvidence(ctx contractapi.TransactionContextInterface,
	bizType, bizId string) (*Evidence, error) {
	return c.mustGetEvidence(ctx, bizType, bizId)
}

// QueryByBizId 按业务类型 + 业务ID 查询存证（与 QueryEvidence 等价，供后端统一调用）
func (c *CharityContract) QueryByBizId(ctx contractapi.TransactionContextInterface,
	bizType, bizId string) (*Evidence, error) {
	return c.mustGetEvidence(ctx, bizType, bizId)
}

// QueryByTxId 按交易ID查询存证（溯源查询：交易ID维度）
func (c *CharityContract) QueryByTxId(ctx contractapi.TransactionContextInterface, txId string) (*Evidence, error) {
	if strings.TrimSpace(txId) == "" {
		return nil, fmt.Errorf("交易ID不能为空")
	}
	rawKey, err := ctx.GetStub().GetState(TxIndexKey(strings.TrimSpace(txId)))
	if err != nil {
		return nil, fmt.Errorf("查询交易索引失败: %v", err)
	}
	if len(rawKey) == 0 {
		return nil, fmt.Errorf("未找到交易ID对应的存证：%s", txId)
	}
	raw, err := ctx.GetStub().GetState(string(rawKey))
	if err != nil {
		return nil, err
	}
	if len(raw) == 0 {
		return nil, fmt.Errorf("交易索引指向的存证已不存在：%s", string(rawKey))
	}
	var evidence Evidence
	if err := json.Unmarshal(raw, &evidence); err != nil {
		return nil, err
	}
	return &evidence, nil
}

// QueryByBlockHeight 按区块高度查询存证（溯源查询：区块高度维度）
func (c *CharityContract) QueryByBlockHeight(ctx contractapi.TransactionContextInterface,
	blockHeight string) ([]*Evidence, error) {
	height := Atos(blockHeight)
	if height <= 0 {
		return nil, fmt.Errorf("区块高度必须为正整数")
	}
	rawIdx, err := ctx.GetStub().GetState(BlockIndexKey(height))
	if err != nil {
		return nil, fmt.Errorf("查询区块高度索引失败: %v", err)
	}
	if len(rawIdx) == 0 {
		return nil, fmt.Errorf("区块高度 %d 未索引到存证记录", height)
	}
	var idx BlockIndex
	if err := json.Unmarshal(rawIdx, &idx); err != nil {
		return nil, err
	}
	result := make([]*Evidence, 0, len(idx.Keys))
	for _, k := range idx.Keys {
		raw, err := ctx.GetStub().GetState(k)
		if err != nil {
			return nil, err
		}
		if len(raw) == 0 {
			continue
		}
		var evidence Evidence
		if err := json.Unmarshal(raw, &evidence); err != nil {
			return nil, err
		}
		result = append(result, &evidence)
	}
	return result, nil
}

// QueryByProjectId 按项目ID查询该项目全部存证（溯源查询：项目维度）
func (c *CharityContract) QueryByProjectId(ctx contractapi.TransactionContextInterface,
	projectId string) ([]*Evidence, error) {
	if strings.TrimSpace(projectId) == "" {
		return nil, fmt.Errorf("项目ID不能为空")
	}
	return c.queryByPrefix(ctx, PrefixProjIdx+"#"+projectId+"#")
}

// QueryAllEvidence 全量分页查询存证（监管方"导出存证数据"）
func (c *CharityContract) QueryAllEvidence(ctx contractapi.TransactionContextInterface,
	pageSize, bookmark string) (*PagedEvidence, error) {
	size := Atos(pageSize)
	if size <= 0 || size > 500 {
		size = 50
	}
	iterator, metadata, err := ctx.GetStub().GetStateByRangeWithPagination(
		PrefixEvidence+"#", PrefixEvidence+"#~", int32(size), bookmark)
	if err != nil {
		return nil, fmt.Errorf("分页查询存证失败: %v", err)
	}
	defer iterator.Close()

	records := make([]*Evidence, 0, size)
	for iterator.HasNext() {
		item, err := iterator.Next()
		if err != nil {
			return nil, err
		}
		var evidence Evidence
		if err := json.Unmarshal(item.Value, &evidence); err != nil {
			continue
		}
		records = append(records, &evidence)
	}
	return &PagedEvidence{
		Records:      records,
		Bookmark:     metadata.GetBookmark(),
		FetchedCount: metadata.GetFetchedRecordsCount(),
	}, nil
}

// ---------------------------------------------------------------------------------------
// 3. 哈希校验 与 全流程溯源
// ---------------------------------------------------------------------------------------

// VerifyHash 哈希验真：将外部提供的文件 / 数据哈希与链上存证哈希比对
func (c *CharityContract) VerifyHash(ctx contractapi.TransactionContextInterface,
	bizType, bizId, providedHash string) (*VerifyResult, error) {
	evidence, err := c.mustGetEvidence(ctx, bizType, bizId)
	if err != nil {
		return nil, err
	}
	normalized := strings.ToLower(strings.TrimSpace(providedHash))
	result := &VerifyResult{
		Matched:      normalized != "" && normalized == strings.ToLower(evidence.DataHash),
		BizType:      evidence.BizType,
		BizId:        evidence.BizId,
		ProvidedHash: normalized,
		StoredHash:   evidence.DataHash,
		TxId:         evidence.TxId,
		BlockHeight:  evidence.BlockHeight,
		ChainTime:    evidence.Timestamp,
		ChainSeq:     evidence.ChainSeq,
	}
	if result.Matched {
		result.Message = "哈希一致，文件未被篡改"
	} else {
		result.Message = "哈希不一致，文件可能已被篡改"
	}
	return result, nil
}

// GetEvidenceHistory 查询单条存证的链上历史变更（可追溯每次状态变更）
func (c *CharityContract) GetEvidenceHistory(ctx contractapi.TransactionContextInterface,
	bizType, bizId string) ([]*HistoryRecord, error) {
	key := EvidenceKey(bizType, bizId)
	iterator, err := ctx.GetStub().GetHistoryForKey(key)
	if err != nil {
		return nil, fmt.Errorf("查询链上历史失败: %v", err)
	}
	defer iterator.Close()

	records := make([]*HistoryRecord, 0)
	for iterator.HasNext() {
		item, err := iterator.Next()
		if err != nil {
			return nil, err
		}
		record := &HistoryRecord{
			TxId:     item.TxId,
			IsDelete: item.IsDelete,
		}
		if item.Timestamp != nil {
			if t, err := ptypes.Timestamp(item.Timestamp); err == nil {
				record.Timestamp = t.UTC().Format(time.RFC3339Nano)
			}
		}
		if !item.IsDelete && len(item.Value) > 0 {
			_ = json.Unmarshal(item.Value, &record.Evidence)
		}
		records = append(records, record)
	}
	// 按链上序号倒序，最新变更在前
	sort.Slice(records, func(i, j int) bool {
		return records[i].Evidence.ChainSeq > records[j].Evidence.ChainSeq
	})
	return records, nil
}

// GetProjectTrace 项目全流程溯源：项目摘要 + 捐赠 + 资金提取 + 资金使用凭证 + 帮扶材料 + 进度
func (c *CharityContract) GetProjectTrace(ctx contractapi.TransactionContextInterface,
	projectId string) (*ProjectTrace, error) {
	list, err := c.QueryByProjectId(ctx, projectId)
	if err != nil {
		return nil, err
	}
	trace := &ProjectTrace{
		ProjectId:     projectId,
		Timeline:      list,
		EvidenceCount: len(list),
		ChainIntact:   true,
	}
	var donationTotal, withdrawalTotal, usageTotal float64
	for _, e := range list {
		if e.BlockHeight > trace.LatestBlock {
			trace.LatestBlock = e.BlockHeight
		}
		switch e.BizType {
		case BizDonation:
			trace.DonationCount++
			donationTotal += parseAmount(e.Amount)
		case BizFundUsage:
			trace.FundUsageCount++
			usageTotal += parseAmount(e.Amount)
		case BizWithdrawal:
			trace.WithdrawalCount++
			withdrawalTotal += parseAmount(e.Amount)
		case BizMaterial:
			trace.MaterialCount++
		}
	}
	// 链式完整性校验：全局链式哈希必须首尾相接
	trace.ChainIntact, _ = c.VerifyChainIntegrity(ctx)
	if len(list) > 0 {
		trace.LatestChainHash = list[len(list)-1].ChainHash
	}
	trace.DonationTotal = formatAmount(donationTotal)
	trace.WithdrawalTotal = formatAmount(withdrawalTotal)
	trace.FundUsageTotal = formatAmount(usageTotal)
	return trace, nil
}

// VerifyChainIntegrity 链式完整性校验：按全局序号逐条校验 PrevChainHash 是否等于上一条 ChainHash。
// 任何一条历史存证被篡改，都会导致后续链哈希断裂。
func (c *CharityContract) VerifyChainIntegrity(ctx contractapi.TransactionContextInterface) (bool, error) {
	iterator, err := ctx.GetStub().GetStateByRange(PrefixSeqIdx+"#", PrefixSeqIdx+"#~")
	if err != nil {
		return false, fmt.Errorf("读取全局序号索引失败: %v", err)
	}
	defer iterator.Close()

	prev := ""
	intact := true
	for iterator.HasNext() {
		item, err := iterator.Next()
		if err != nil {
			return false, err
		}
		raw, err := ctx.GetStub().GetState(string(item.Value))
		if err != nil {
			return false, err
		}
		if len(raw) == 0 {
			continue
		}
		var evidence Evidence
		if err := json.Unmarshal(raw, &evidence); err != nil {
			continue
		}
		if prev != "" && evidence.PrevChainHash != prev {
			intact = false
			break
		}
		if computeChainHash(evidence.PrevChainHash,evidence.Key,evidence.DataHash,evidence.TxId,evidence.Timestamp,evidence.ChainSeq)!=evidence.ChainHash{return false,nil}
		prev = evidence.ChainHash
	}
	return intact, nil
}

// GetChainStats 链上存证统计（审计概览 / 数据统计报表）
func (c *CharityContract) GetChainStats(ctx contractapi.TransactionContextInterface) (*ChainStats, error) {
	stats := &ChainStats{
		ByBizType:    map[string]int64{},
		ChaincodeVer: ChaincodeVersion,
	}
	iterator, err := ctx.GetStub().GetStateByRange(PrefixEvidence+"#", PrefixEvidence+"#~")
	if err != nil {
		return nil, fmt.Errorf("统计链上存证失败: %v", err)
	}
	defer iterator.Close()

	projects := map[string]bool{}
	for iterator.HasNext() {
		item, err := iterator.Next()
		if err != nil {
			return nil, err
		}
		var evidence Evidence
		if err := json.Unmarshal(item.Value, &evidence); err != nil {
			continue
		}
		stats.TotalEvidence++
		stats.ByBizType[evidence.BizType]++
		if evidence.ProjectId != "" {
			projects[evidence.ProjectId] = true
		}
		if evidence.ChainSeq > stats.LatestSeq {
			stats.LatestSeq = evidence.ChainSeq
			stats.LatestHash = evidence.ChainHash
		}
	}
	stats.ProjectCount = int64(len(projects))
	return stats, nil
}

// EvidenceExists 判断某业务是否已存证
func (c *CharityContract) EvidenceExists(ctx contractapi.TransactionContextInterface,
	bizType, bizId string) (bool, error) {
	raw, err := ctx.GetStub().GetState(EvidenceKey(bizType, bizId))
	if err != nil {
		return false, err
	}
	return len(raw) > 0, nil
}

// ---------------------------------------------------------------------------------------
// 4. 链码初始化 / 升级
// ---------------------------------------------------------------------------------------

// InitLedger 链码实例化时写入创世链头
func (c *CharityContract) InitLedger(ctx contractapi.TransactionContextInterface) error {
	head, err := ctx.GetStub().GetState(PrefixHead)
	if err != nil {
		return err
	}
	if len(head) > 0 {
		return nil
	}
	// 创世哈希：sha256("charity-cc-genesis")
	genesis := sha256.Sum256([]byte("charity-cc-genesis"))
	if err := ctx.GetStub().PutState(PrefixHead, []byte(hex.EncodeToString(genesis[:]))); err != nil {
		return err
	}
	if err := ctx.GetStub().PutState(PrefixSeq, []byte("0")); err != nil {
		return err
	}
	return nil
}

// GetChaincodeVersion 返回当前链码版本（管理员"部署合约升级"后可校验）
func (c *CharityContract) GetChaincodeVersion(contractapi.TransactionContextInterface) (string, error) {
	return ChaincodeVersion, nil
}

// ---------------------------------------------------------------------------------------
// 内部辅助
// ---------------------------------------------------------------------------------------

func (c *CharityContract) mustGetEvidence(ctx contractapi.TransactionContextInterface,
	bizType, bizId string) (*Evidence, error) {
	key := EvidenceKey(bizType, bizId)
	raw, err := ctx.GetStub().GetState(key)
	if err != nil {
		return nil, fmt.Errorf("查询链上存证失败: %v", err)
	}
	if len(raw) == 0 {
		return nil, fmt.Errorf("链上存证不存在：%s", key)
	}
	var evidence Evidence
	if err := json.Unmarshal(raw, &evidence); err != nil {
		return nil, fmt.Errorf("解析链上存证失败: %v", err)
	}
	return &evidence, nil
}

func (c *CharityContract) queryByPrefix(ctx contractapi.TransactionContextInterface, prefix string) ([]*Evidence, error) {
	iterator, err := ctx.GetStub().GetStateByRange(prefix, prefix+"~")
	if err != nil {
		return nil, fmt.Errorf("范围查询失败: %v", err)
	}
	defer iterator.Close()

	result := make([]*Evidence, 0)
	for iterator.HasNext() {
		item, err := iterator.Next()
		if err != nil {
			return nil, err
		}
		raw, err := ctx.GetStub().GetState(string(item.Value))
		if err != nil {
			return nil, err
		}
		if len(raw) == 0 {
			continue
		}
		var evidence Evidence
		if err := json.Unmarshal(raw, &evidence); err != nil {
			continue
		}
		result = append(result, &evidence)
	}
	sort.Slice(result, func(i, j int) bool { return result[i].ChainSeq < result[j].ChainSeq })
	return result, nil
}

func (c *CharityContract) nextChainSeq(ctx contractapi.TransactionContextInterface) (int64, error) {
	raw, err := ctx.GetStub().GetState(PrefixSeq)
	if err != nil {
		return 0, fmt.Errorf("读取存证序号失败: %v", err)
	}
	seq := int64(0)
	if len(raw) > 0 {
		seq = Atos(string(raw))
	}
	seq++
	if err := ctx.GetStub().PutState(PrefixSeq, []byte(strconv.FormatInt(seq, 10))); err != nil {
		return 0, fmt.Errorf("更新存证序号失败: %v", err)
	}
	return seq, nil
}

func (c *CharityContract) chainHead(ctx contractapi.TransactionContextInterface) (string, error) {
	raw, err := ctx.GetStub().GetState(PrefixHead)
	if err != nil {
		return "", fmt.Errorf("读取链头失败: %v", err)
	}
	if len(raw) == 0 {
		return strings.Repeat("0", 64), nil
	}
	return string(raw), nil
}

func (c *CharityContract) increaseStat(ctx contractapi.TransactionContextInterface, bizType string) error {
	key := PrefixStats + "#" + bizType
	raw, err := ctx.GetStub().GetState(key)
	if err != nil {
		return err
	}
	count := int64(0)
	if len(raw) > 0 {
		count = Atos(string(raw))
	}
	count++
	return ctx.GetStub().PutState(key, []byte(strconv.FormatInt(count, 10)))
}

func transactionTime(ctx contractapi.TransactionContextInterface) (time.Time, error) {
	ts, err := ctx.GetStub().GetTxTimestamp()
	if err != nil {
		return time.Time{}, fmt.Errorf("获取交易时间戳失败: %v", err)
	}
	if ts == nil {
		// 单元测试（MockStub）等场景未设置交易时间戳时，退化为当前时间
		return time.Now().UTC(), nil
	}
	t, err := ptypes.Timestamp(ts)
	if err != nil {
		return time.Time{}, fmt.Errorf("解析交易时间戳失败: %v", err)
	}
	return t.UTC(), nil
}

func computeChainHash(prevHash, key, dataHash, txId, timestamp string, seq int64) string {
	payload := fmt.Sprintf("%s|%s|%s|%s|%s|%d", prevHash, key, dataHash, txId, timestamp, seq)
	sum := sha256.Sum256([]byte(payload))
	return hex.EncodeToString(sum[:])
}

func buildChainResult(e *Evidence) *ChainResult {
	return &ChainResult{
		TxId:        e.TxId,
		BizType:     e.BizType,
		BizId:       e.BizId,
		ProjectId:   e.ProjectId,
		DataHash:    e.DataHash,
		ChainSeq:    e.ChainSeq,
		BlockHeight: e.BlockHeight,
		Timestamp:   e.Timestamp,
		ChainHash:   e.ChainHash,
		EvidenceKey: e.Key,
		EvidenceNo:  BuildEvidenceNo(e.ChainSeq),
		Status:      e.Status,
	}
}

func validateAmount(field, value string) error {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return fmt.Errorf("字段 %s 不能为空", field)
	}
	amount, err := strconv.ParseFloat(trimmed, 64)
	if err != nil {
		return fmt.Errorf("字段 %s 必须为合法数字", field)
	}
	if amount < 0 {
		return fmt.Errorf("字段 %s 不能为负数", field)
	}
	return nil
}

func normalizeAmount(value string) string {
	amount, err := strconv.ParseFloat(strings.TrimSpace(value), 64)
	if err != nil {
		return strings.TrimSpace(value)
	}
	return strconv.FormatFloat(amount, 'f', 2, 64)
}

func parseAmount(value string) float64 {
	if strings.TrimSpace(value) == "" {
		return 0
	}
	amount, err := strconv.ParseFloat(strings.TrimSpace(value), 64)
	if err != nil {
		return 0
	}
	return amount
}

func formatAmount(value float64) string {
	return strconv.FormatFloat(value, 'f', 2, 64)
}
