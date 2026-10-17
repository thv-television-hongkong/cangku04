/*
Package main 是校园公益众筹存证溯源系统的 Hyperledger Fabric 链码入口。

链码名称：charity-cc
链码职责：
 1. 众筹项目摘要哈希上链存证            CreateProjectEvidence / UpdateProjectStatus
 2. 捐赠记录哈希上链存证                CreateDonationEvidence
 3. 资金使用（报销）凭证哈希上链存证    CreateFundUsageEvidence
 4. 帮扶 / 项目证明材料哈希上链存证     CreateMaterialEvidence
 5. 资金提取记录哈希上链存证            CreateWithdrawalEvidence
 6. 项目进度哈希上链存证                CreateProjectProgressEvidence
 7. 审计报告哈希上链存证                CreateReportEvidence
 8. 多维度查询：业务ID / 交易ID / 区块高度 / 项目ID / 全量分页
 9. 哈希校验：VerifyHash
 10. 全流程溯源：GetProjectTrace / GetEvidenceHistory

法律约束落地：链码只接受 64 位 SHA-256 摘要与非敏感的金额、状态等字段；
任何包含姓名、手机号、身份证号、学号、银行卡号、住址、邮箱等明文的载荷都会被拒绝
（见 privacy.go）。
*/
package main

import (
	"fmt"
	"log"

	"github.com/campus/charity-chaincode/contract"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

func main() {
	chaincode, err := contractapi.NewChaincode(new(contract.CharityContract))
	if err != nil {
		log.Fatalf("创建 charity-cc 链码失败: %v", err)
	}

	if err := chaincode.Start(); err != nil {
		log.Fatalf("启动 charity-cc 链码失败: %s", fmt.Sprintf("%v", err))
	}
}
