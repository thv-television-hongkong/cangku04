package contract

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/golang/protobuf/ptypes"
	"github.com/hyperledger/fabric-chaincode-go/shimtest"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"github.com/stretchr/testify/require"
)

const (
	hashDonation = "1111111111111111111111111111111111111111111111111111111111111111"
	hashProject  = "2222222222222222222222222222222222222222222222222222222222222222"
	hashFile     = "3333333333333333333333333333333333333333333333333333333333333333"
	hashDonor    = "4444444444444444444444444444444444444444444444444444444444444444"
	hashAmount   = "5555555555555555555555555555555555555555555555555555555555555555"
	hashUsage    = "6666666666666666666666666666666666666666666666666666666666666666"
	hashMaterial = "7777777777777777777777777777777777777777777777777777777777777777"
)

func newMockStub(t *testing.T) *shimtest.MockStub {
	t.Helper()
	cc, err := contractapi.NewChaincode(new(CharityContract))
	require.NoError(t, err)
	stub := shimtest.NewMockStub("charity-cc", cc)
	ts, err := ptypes.TimestampProto(time.Date(2024, 5, 20, 10, 0, 0, 0, time.UTC))
	require.NoError(t, err)
	stub.TxTimestamp = ts
	return stub
}

func invoke(t *testing.T, stub *shimtest.MockStub, txId, fn string, args ...string) []byte {
	t.Helper()
	raw := make([][]byte, 0, len(args)+1)
	raw = append(raw, []byte(fn))
	for _, a := range args {
		raw = append(raw, []byte(a))
	}
	res := stub.MockInvoke(txId, raw)
	require.Equalf(t, int32(200), res.Status, "链码调用 %s 失败: %s", fn, string(res.Message))
	return res.Payload
}

// TestCreateAndQueryDonationEvidence 捐赠哈希上链 -> 按业务ID / 交易ID 查询
func TestCreateAndQueryDonationEvidence(t *testing.T) {
	stub := newMockStub(t)
	invoke(t, stub, "tx-init", "InitLedger")

	payload := invoke(t, stub, "tx-donation-1", "CreateDonationEvidence",
		"DON001", "PRJ001", hashDonor, "500.00", hashAmount, hashDonation, "20210001", "STUDENT")

	var result ChainResult
	require.NoError(t, json.Unmarshal(payload, &result))
	require.Equal(t, "tx-donation-1", result.TxId)
	require.Equal(t, "DONATION", result.BizType)
	require.Equal(t, hashDonation, result.DataHash)
	require.NotEmpty(t, result.ChainHash)
	require.EqualValues(t, 1, result.ChainSeq)

	// 按业务ID查询
	byBiz := invoke(t, stub, "tx-q1", "QueryByBizId", "DONATION", "DON001")
	var ev Evidence
	require.NoError(t, json.Unmarshal(byBiz, &ev))
	require.Equal(t, "DON001", ev.BizId)
	require.Equal(t, "PRJ001", ev.ProjectId)

	// 按交易ID查询
	byTx := invoke(t, stub, "tx-q2", "QueryByTxId", "tx-donation-1")
	var ev2 Evidence
	require.NoError(t, json.Unmarshal(byTx, &ev2))
	require.Equal(t, ev.Key, ev2.Key)

	// 存在性判断
	exists := invoke(t, stub, "tx-q3", "EvidenceExists", "DONATION", "DON001")
	require.Equal(t, "true", string(exists))
}

// TestVerifyHashDetectsTampering 哈希验真：一致通过、篡改失败
func TestVerifyHashDetectsTampering(t *testing.T) {
	stub := newMockStub(t)
	invoke(t, stub, "tx-init", "InitLedger")
	invoke(t, stub, "tx-donation-1", "CreateDonationEvidence",
		"DON001", "PRJ001", hashDonor, "500.00", hashAmount, hashDonation, "20210001", "STUDENT")

	okPayload := invoke(t, stub, "tx-v1", "VerifyHash", "DONATION", "DON001", hashDonation)
	var ok VerifyResult
	require.NoError(t, json.Unmarshal(okPayload, &ok))
	require.True(t, ok.Matched)
	require.Equal(t, "哈希一致，文件未被篡改", ok.Message)

	badPayload := invoke(t, stub, "tx-v2", "VerifyHash", "DONATION", "DON001",
		"9999999999999999999999999999999999999999999999999999999999999999")
	var bad VerifyResult
	require.NoError(t, json.Unmarshal(badPayload, &bad))
	require.False(t, bad.Matched)
	require.Contains(t, bad.Message, "篡改")
}

// TestPrivacyValidationRejectsPersonalData 隐私字段 / 敏感明文不得上链
func TestPrivacyValidationRejectsPersonalData(t *testing.T) {
	// 1) 非哈希的捐赠人标识（如姓名）被拒绝
	err := ValidateEvidenceInput(&EvidenceInput{
		BizType: BizDonation, BizId: "DON002", ProjectId: "PRJ001",
		DataHash: hashDonation, DonorHash: "张三",
	})
	require.Error(t, err)
	require.Contains(t, err.Error(), "SHA-256")

	// 2) 扩展字段出现姓名字段被拒绝
	err = ValidateEvidenceInput(&EvidenceInput{
		BizType: BizDonation, BizId: "DON003", ProjectId: "PRJ001",
		DataHash: hashDonation,
		Extra:    map[string]string{"realName": "李四"},
	})
	require.Error(t, err)
	require.Contains(t, err.Error(), "姓名")

	// 3) 值中出现手机号明文被拒绝
	err = ValidateEvidenceInput(&EvidenceInput{
		BizType: BizDonation, BizId: "DON004", ProjectId: "PRJ001",
		DataHash: hashDonation,
		Extra:    map[string]string{"contactInfo": "13800138000"},
	})
	require.Error(t, err)

	// 4) 合法的纯哈希 + 非敏感字段可以通过
	require.NoError(t, ValidateEvidenceInput(&EvidenceInput{
		BizType: BizDonation, BizId: "DON005", ProjectId: "PRJ001",
		DataHash: hashDonation, FileHash: hashFile, Amount: "500.00",
		Operator: "20210001", OperatorRole: "STUDENT",
	}))
}

// TestProjectTraceAndFundChain 项目全流程溯源 + 资金链路统计
func TestProjectTraceAndFundChain(t *testing.T) {
	stub := newMockStub(t)
	invoke(t, stub, "tx-init", "InitLedger")

	// 项目存证
	invoke(t, stub, "tx-1", "CreateProjectEvidence",
		"PRJ001", hashProject, "ONLINE", "admin", "ADMIN")
	// 捐赠存证 x2
	invoke(t, stub, "tx-2", "CreateDonationEvidence",
		"DON001", "PRJ001", hashDonor, "500.00", hashAmount, hashDonation, "20210001", "STUDENT")
	invoke(t, stub, "tx-3", "CreateDonationEvidence",
		"DON002", "PRJ001", hashDonor, "300.50", hashAmount, hashDonation, "20210002", "STUDENT")
	// 资金提取
	invoke(t, stub, "tx-4", "CreateWithdrawalEvidence",
		"WIT001", "PRJ001", "400.00", hashUsage, "APPROVED", "club001", "CLUB")
	// 资金使用凭证
	invoke(t, stub, "tx-5", "CreateFundUsageEvidence",
		"FUN001", "PRJ001", "400.00", hashFile, hashUsage, "APPROVED", "club001", "CLUB")
	// 帮扶材料
	invoke(t, stub, "tx-6", "CreateMaterialEvidence",
		"MAT001", "PRJ001", "BENEFICIARY", hashFile, hashMaterial, "club001", "CLUB")

	payload := invoke(t, stub, "tx-trace", "GetProjectTrace", "PRJ001")
	var trace ProjectTrace
	require.NoError(t, json.Unmarshal(payload, &trace))
	require.Equal(t, 6, trace.EvidenceCount)
	require.Equal(t, 2, trace.DonationCount)
	require.Equal(t, 1, trace.FundUsageCount)
	require.Equal(t, 1, trace.WithdrawalCount)
	require.Equal(t, 1, trace.MaterialCount)
	require.Equal(t, "800.50", trace.DonationTotal)
	require.Equal(t, "400.00", trace.WithdrawalTotal)
	require.True(t, trace.ChainIntact)
	require.Len(t, trace.Timeline, 6)

	// 链式完整性
	ok := invoke(t, stub, "tx-intact", "VerifyChainIntegrity")
	require.Equal(t, "true", string(ok))

	// 统计
	statsPayload := invoke(t, stub, "tx-stats", "GetChainStats")
	var stats ChainStats
	require.NoError(t, json.Unmarshal(statsPayload, &stats))
	require.EqualValues(t, 6, stats.TotalEvidence)
	require.EqualValues(t, 2, stats.ByBizType[BizDonation])
	require.EqualValues(t, 1, stats.ProjectCount)
	require.Equal(t, ChaincodeVersion, stats.ChaincodeVer)
}

// TestIndexBlockHeightAndQuery 区块高度索引与按区块高度查询
func TestIndexBlockHeightAndQuery(t *testing.T) {
	stub := newMockStub(t)
	invoke(t, stub, "tx-init", "InitLedger")
	invoke(t, stub, "tx-1", "CreateDonationEvidence",
		"DON001", "PRJ001", hashDonor, "500.00", hashAmount, hashDonation, "20210001", "STUDENT")

	// 后端在交易提交后回填真实区块高度
	idxPayload := invoke(t, stub, "tx-idx", "IndexEvidenceBlockHeight", "DONATION", "DON001", "128", "tx-1")
	var idxResult ChainResult
	require.NoError(t, json.Unmarshal(idxPayload, &idxResult))
	require.EqualValues(t, 128, idxResult.BlockHeight)

	queryPayload := invoke(t, stub, "tx-q", "QueryByBlockHeight", "128")
	var list []*Evidence
	require.NoError(t, json.Unmarshal(queryPayload, &list))
	require.Len(t, list, 1)
	require.Equal(t, "DON001", list[0].BizId)
	require.EqualValues(t, 128, list[0].BlockHeight)
}

// TestIdempotentEvidence 相同哈希重复提交不会重复上链
func TestIdempotentEvidence(t *testing.T) {
	stub := newMockStub(t)
	invoke(t, stub, "tx-init", "InitLedger")
	first := invoke(t, stub, "tx-1", "CreateDonationEvidence",
		"DON001", "PRJ001", hashDonor, "500.00", hashAmount, hashDonation, "20210001", "STUDENT")
	second := invoke(t, stub, "tx-2", "CreateDonationEvidence",
		"DON001", "PRJ001", hashDonor, "500.00", hashAmount, hashDonation, "20210001", "STUDENT")

	var r1, r2 ChainResult
	require.NoError(t, json.Unmarshal(first, &r1))
	require.NoError(t, json.Unmarshal(second, &r2))
	require.Equal(t, r1.TxId, r2.TxId)
	require.Equal(t, r1.ChainSeq, r2.ChainSeq)

	statsPayload := invoke(t, stub, "tx-stats", "GetChainStats")
	var stats ChainStats
	require.NoError(t, json.Unmarshal(statsPayload, &stats))
	require.EqualValues(t, 1, stats.TotalEvidence)
}

// TestProjectStatusEvidence 项目状态变更（下架）形成可溯源记录
func TestProjectStatusEvidence(t *testing.T) {
	stub := newMockStub(t)
	invoke(t, stub, "tx-init", "InitLedger")
	invoke(t, stub, "tx-1", "CreateProjectEvidence",
		"PRJ009", hashProject, "ONLINE", "admin", "ADMIN")
	invoke(t, stub, "tx-2", "UpdateProjectStatus",
		"PRJ009", "OFFLINE", hashProject, "未提供帮扶对象核实材料", "admin", "ADMIN")

	tracePayload := invoke(t, stub, "tx-trace", "GetProjectTrace", "PRJ009")
	var trace ProjectTrace
	require.NoError(t, json.Unmarshal(tracePayload, &trace))
	require.Equal(t, 2, trace.EvidenceCount)

	byBiz := invoke(t, stub, "tx-q", "QueryByBizId", BizProjectStatus, "PRJ009")
	var ev Evidence
	require.NoError(t, json.Unmarshal(byBiz, &ev))
	require.Equal(t, "OFFLINE", ev.Status)

	// 非法业务类型被拒绝
	res := stub.MockInvoke("tx-bad", [][]byte{[]byte("CreateProjectEvidence"),
		[]byte("P1"), []byte(hashProject), []byte("ONLINE"), []byte("admin"), []byte("ADMIN")})
	require.Equal(t, int32(200), res.Status)

	bad := stub.MockInvoke("tx-bad2", [][]byte{[]byte("QueryByBizId"),
		[]byte("UNKNOWN"), []byte("1")})
	require.NotEqual(t, int32(200), bad.Status)
}
