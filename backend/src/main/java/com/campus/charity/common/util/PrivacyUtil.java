package com.campus.charity.common.util;

import cn.hutool.core.util.StrUtil;
import com.campus.charity.common.api.ResultCode;
import com.campus.charity.common.exception.BizException;
import com.campus.charity.fabric.dto.EvidencePayload;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 隐私合规校验（与 Go 链码 contract/privacy.go 规则保持一致，形成“链下预检 + 链上强制”双重保障）。
 *
 * <p>法律可行性约束：</p>
 * <ul>
 *   <li>仅文件哈希摘要上链，原始文件保存在业务服务器；</li>
 *   <li>帮扶对象个人信息脱敏展示，区块链不存储原始敏感隐私；</li>
 *   <li>用户学号、社团、捐赠信息仅限校内公益业务使用。</li>
 * </ul>
 */
public final class PrivacyUtil {

    /** 禁止上链的字段名 */
    private static final Set<String> FORBIDDEN_KEYS = new HashSet<>(Arrays.asList(
            "realname", "name", "username", "account", "password", "phone", "mobile", "telephone",
            "idcard", "idcardno", "idnumber", "idno", "studentno", "studentid", "studentnumber",
            "schoolno", "address", "homeaddress", "bankcard", "bankcardno", "bankaccount",
            "email", "mail", "beneficiary", "beneficiaryname", "contact", "wechat", "qq", "idphoto"));

    /** 允许承载中文明文的非敏感字段 */
    private static final Set<String> ALLOWED_PLAIN_KEYS = new HashSet<>(Arrays.asList(
            "biztype", "bizid", "projectid", "datahash", "filehash", "amounthash", "donorhash",
            "operator", "operatorrole", "status", "remark", "reason", "note", "message", "txid",
            "chainseq", "blockheight", "timestamp", "prevchainhash", "chainhash", "version", "amount",
            "materials", "materialtype", "stagename", "reporttype", "evidencekey", "evidenceno",
            "evidencetype", "key", "channel", "chaincode", "function"));

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern ID_CARD = Pattern.compile(
            "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$");
    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern LONG_DIGIT = Pattern.compile("^\\d{15,19}$");
    private static final Pattern CHINESE = Pattern.compile("[\\u4e00-\\u9fa5]");

    private PrivacyUtil() {
    }

    /** 存证载荷合规校验 */
    public static void validate(EvidencePayload payload) {
        if (payload == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "存证载荷不能为空");
        }
        if (StrUtil.isBlank(payload.getBizId())) {
            throw new BizException(ResultCode.BAD_REQUEST, "存证业务ID不能为空");
        }
        requireSha256("dataHash", payload.getDataHash());
        if (StrUtil.isNotBlank(payload.getFileHash())) {
            requireSha256("fileHash", payload.getFileHash());
        }
        if (StrUtil.isNotBlank(payload.getAmountHash())) {
            requireSha256("amountHash", payload.getAmountHash());
        }
        if (StrUtil.isNotBlank(payload.getDonorHash())) {
            requireSha256("donorHash", payload.getDonorHash());
        }
        if (payload.getExtra() != null) {
            for (Map.Entry<String, String> entry : payload.getExtra().entrySet()) {
                checkField(entry.getKey(), entry.getValue());
            }
        }
    }

    /** 单字段校验 */
    public static void checkField(String key, String value) {
        if (StrUtil.isBlank(value)) {
            return;
        }
        String lower = key.toLowerCase().trim();
        if (lower.endsWith("hash")) {
            return;
        }
        if (FORBIDDEN_KEYS.contains(lower)) {
            throw new BizException(ResultCode.PRIVACY_REJECTED,
                    "隐私校验未通过：禁止将字段【" + key + "】写入区块链，该信息必须脱敏后仅存业务库");
        }
        String trimmed = value.trim();
        if (ID_CARD.matcher(trimmed).matches()) {
            throw new BizException(ResultCode.PRIVACY_REJECTED, "隐私校验未通过：字段【" + key + "】疑似身份证号明文");
        }
        if (MOBILE.matcher(trimmed).matches()) {
            throw new BizException(ResultCode.PRIVACY_REJECTED, "隐私校验未通过：字段【" + key + "】疑似手机号明文");
        }
        if (EMAIL.matcher(trimmed).matches()) {
            throw new BizException(ResultCode.PRIVACY_REJECTED, "隐私校验未通过：字段【" + key + "】疑似邮箱明文");
        }
        if (LONG_DIGIT.matcher(trimmed).matches()) {
            throw new BizException(ResultCode.PRIVACY_REJECTED, "隐私校验未通过：字段【" + key + "】疑似学号/银行卡号明文");
        }
        if (!ALLOWED_PLAIN_KEYS.contains(lower) && CHINESE.matcher(trimmed).find()) {
            throw new BizException(ResultCode.PRIVACY_REJECTED,
                    "隐私校验未通过：未知字段【" + key + "】不允许写入中文明文，请仅上链哈希摘要或编码值");
        }
    }

    private static void requireSha256(String field, String value) {
        if (StrUtil.isBlank(value) || !SHA256.matcher(value).matches()) {
            throw new BizException(ResultCode.PRIVACY_REJECTED,
                    "隐私校验未通过：字段【" + field + "】必须为 64 位十六进制 SHA-256 摘要");
        }
    }
}
