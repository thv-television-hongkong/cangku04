package contract

import (
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"unicode"
)

// ---------------------------------------------------------------------------------------
// 法律可行性约束的链码级强制校验
//
// 需求原文：
//   * "仅文件哈希摘要上链，原始文件保存在业务服务器"
//   * "帮扶对象个人信息做脱敏展示，区块链不存储原始敏感隐私"
//   * "用户学号、社团、捐赠信息仅限校内公益业务使用，禁止泄露倒卖用户隐私"
//
// 因此链码对每一笔上链载荷执行三重校验：
//   1. JSON 键名黑名单校验：出现 realName / idCard / studentNo / phone 等键直接拒绝交易；
//   2. 值内容模式校验：出现 18 位身份证号、11 位手机号、邮箱、15~19 位纯数字（银行卡号）
//      等敏感明文直接拒绝交易；
//   3. 白名单之外的未知字段不允许承载中文明文，避免换个键名绕过隐私限制。
// 哈希字段（*Hash / txId / chainHash 等）为 64 位十六进制摘要，属于允许上链内容。
// ---------------------------------------------------------------------------------------

var forbiddenKeys = map[string]string{
	"realname":        "姓名",
	"name":            "姓名",
	"username":        "账号",
	"account":         "账号",
	"password":        "密码",
	"phone":           "手机号",
	"mobile":          "手机号",
	"telephone":       "联系电话",
	"idcard":          "身份证号",
	"idcardno":        "身份证号",
	"idnumber":        "证件号码",
	"idno":            "证件号码",
	"studentno":       "学号",
	"studentid":       "学号",
	"studentnumber":   "学号",
	"schoolno":        "学号",
	"address":         "详细住址",
	"homeaddress":     "详细住址",
	"bankcard":        "银行卡号",
	"bankcardno":      "银行卡号",
	"bankaccount":     "银行账号",
	"email":           "邮箱",
	"mail":            "邮箱",
	"beneficiary":     "帮扶对象姓名",
	"beneficiaryname": "帮扶对象姓名",
	"contact":         "联系方式",
	"wechat":          "社交账号",
	"qq":              "社交账号",
	"idphoto":         "证件照片",
}

// allowedKeys 允许承载中文明文的非敏感字段（系统模板生成的说明、状态、类型等）
var allowedKeys = map[string]bool{
	"biztype": true, "bizid": true, "projectid": true, "datahash": true, "filehash": true,
	"amounthash": true, "donorhash": true, "operator": true, "operatorrole": true,
	"status": true, "remark": true, "reason": true, "note": true, "message": true,
	"txid": true, "chainseq": true, "blockheight": true,
	"timestamp": true, "prevchainhash": true, "chainhash": true, "version": true,
	"amount": true, "materials": true, "materialtype": true, "stagename": true,
	"reporttype": true, "evidencekey": true, "evidenceno": true, "evidencetype": true,
	"key": true, "channel": true, "chaincode": true, "function": true,
}

var (
	// 18 位身份证号（末位可为 X）
	reIDCard = regexp.MustCompile(`^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]$`)
	// 11 位手机号
	reMobile = regexp.MustCompile(`^1[3-9]\d{9}$`)
	// 邮箱
	reEmail = regexp.MustCompile(`^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$`)
	// 64 位十六进制哈希
	reHash64 = regexp.MustCompile(`^[0-9a-fA-F]{64}$`)
)

// IsHashField 判断是否为哈希/交易ID等允许承载十六进制摘要的字段
func IsHashField(key string) bool {
	k := strings.ToLower(strings.TrimSpace(key))
	if strings.HasSuffix(k, "hash") {
		return true
	}
	switch k {
	case "txid", "chainhash", "prevchainhash", "evidencekey", "key", "blockheight", "chainseq":
		return true
	}
	return false
}

// ValidateHash256 校验 64 位十六进制 SHA-256 摘要
func ValidateHash256(field, value string) error {
	if value == "" {
		return fmt.Errorf("字段 %s 的哈希摘要不能为空", field)
	}
	if !reHash64.MatchString(strings.TrimSpace(value)) {
		return fmt.Errorf("字段 %s 必须为 64 位十六进制 SHA-256 摘要（实际长度 %d）", field, len(strings.TrimSpace(value)))
	}
	return nil
}

// validateField 单字段隐私校验
func validateField(key, value string) error {
	if value == "" {
		return nil
	}
	if IsHashField(key) {
		return nil
	}

	// Nested payload paths (for example Extra.realName) are checked by their
	// terminal field name so the privacy error remains explicit and consistent.
	lowerKey := strings.ToLower(strings.TrimSpace(key))
	fieldName := lowerKey
	if dot := strings.LastIndex(fieldName, "."); dot >= 0 {
		fieldName = fieldName[dot+1:]
	}
	if bracket := strings.IndexByte(fieldName, '['); bracket >= 0 {
		fieldName = fieldName[:bracket]
	}
	if label, bad := forbiddenKeys[fieldName]; bad {
		return fmt.Errorf("隐私校验未通过：链上禁止写入字段 %s（%s），该信息必须脱敏后仅存业务库", key, label)
	}

	trimmed := strings.TrimSpace(value)

	if reIDCard.MatchString(trimmed) {
		return fmt.Errorf("隐私校验未通过：字段 %s 疑似包含身份证号明文，链上仅允许哈希摘要", key)
	}
	if reMobile.MatchString(trimmed) {
		return fmt.Errorf("隐私校验未通过：字段 %s 疑似包含手机号明文，链上仅允许哈希摘要", key)
	}
	if reEmail.MatchString(trimmed) {
		return fmt.Errorf("隐私校验未通过：字段 %s 疑似包含邮箱明文，链上仅允许哈希摘要", key)
	}
	// 银行卡号 / 未识别证件号：15~19 位纯数字（金额、序号等字段不受影响）
	if isAllDigits(trimmed) && len(trimmed) >= 15 && len(trimmed) <= 19 {
		return fmt.Errorf("隐私校验未通过：字段 %s 疑似包含银行卡号/证件号明文，链上仅允许哈希摘要", key)
	}
	// 未知字段禁止写入中文姓名类明文：白名单字段之外的字段一律只接受编码值与哈希
	if !allowedKeys[lowerKey] && containsChinese(trimmed) {
		return fmt.Errorf("隐私校验未通过：未知字段 %s 不允许写入中文明文，请仅上链哈希摘要或编码值", key)
	}
	return nil
}

func isAllDigits(s string) bool {
	for _, r := range s {
		if r < '0' || r > '9' {
			return false
		}
	}
	return len(s) > 0
}

func containsChinese(s string) bool {
	for _, r := range s {
		if unicode.Is(unicode.Han, r) {
			return true
		}
	}
	return false
}

// ValidateEvidenceInput 校验整条存证输入：哈希格式 + 隐私黑名单
func ValidateEvidenceInput(input *EvidenceInput) error {
	if input == nil {
		return fmt.Errorf("存证输入不能为空")
	}
	if strings.TrimSpace(input.BizId) == "" {
		return fmt.Errorf("业务ID（bizId）不能为空")
	}
	if !IsSupportedBizType(input.BizType) {
		return fmt.Errorf("不支持的业务类型：%s", input.BizType)
	}
	if err := ValidateHash256("dataHash", input.DataHash); err != nil {
		return err
	}
	if input.FileHash != "" {
		if err := ValidateHash256("fileHash", input.FileHash); err != nil {
			return err
		}
	}
	if input.AmountHash != "" {
		if err := ValidateHash256("amountHash", input.AmountHash); err != nil {
			return err
		}
	}
	if input.DonorHash != "" {
		if err := ValidateHash256("donorHash", input.DonorHash); err != nil {
			return err
		}
	}

	payload, err := json.Marshal(input)
	if err != nil {
		return fmt.Errorf("存证载荷序列化失败: %v", err)
	}
	if err := ValidatePayloadJSON(string(payload)); err != nil {
		return err
	}
	if input.Extra != nil {
		for k, v := range input.Extra {
			if err := validateField(k, v); err != nil {
				return err
			}
		}
	}
	return nil
}

// ValidatePayloadJSON 对任意 JSON 存证载荷执行递归隐私校验
// 后端在写入前调用本函数做前置校验时，可直接以 JSON 字符串作为入参。
func ValidatePayloadJSON(payload string) error {
	trimmed := strings.TrimSpace(payload)
	if trimmed == "" {
		return fmt.Errorf("存证载荷不能为空")
	}
	var data map[string]interface{}
	if err := json.Unmarshal([]byte(trimmed), &data); err != nil {
		return fmt.Errorf("存证载荷必须为合法 JSON：仅允许写入哈希摘要与非敏感字段")
	}
	return walkJSON("", data)
}

func walkJSON(prefix string, value interface{}) error {
	switch v := value.(type) {
	case map[string]interface{}:
		for k, sub := range v {
			key := k
			if prefix != "" {
				key = prefix + "." + k
			}
			if err := walkJSON(key, sub); err != nil {
				return err
			}
		}
		return nil
	case []interface{}:
		for i, sub := range v {
			if err := walkJSON(fmt.Sprintf("%s[%d]", prefix, i), sub); err != nil {
				return err
			}
		}
		return nil
	case string:
		return validateField(prefix, v)
	case float64:
		return nil
	case bool:
		return nil
	case nil:
		return nil
	default:
		return nil
	}
}

// IsSupportedBizType 校验业务类型
func IsSupportedBizType(bizType string) bool {
	switch strings.ToUpper(strings.TrimSpace(bizType)) {
	case BizProject, BizDonation, BizFundUsage, BizMaterial, BizWithdrawal,
		BizProjectProgress, BizAuditReport, BizProjectStatus, BizRefund:
		return true
	default:
		return false
	}
}

// NormalizeBizType 业务类型归一化
func NormalizeBizType(bizType string) string {
	return strings.ToUpper(strings.TrimSpace(bizType))
}
