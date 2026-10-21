package com.elderlycare.platform.elder.service;

import com.elderlycare.platform.common.crypto.DataKeyRing;
import com.elderlycare.platform.elder.domain.ElderProfileData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class ElderCrypto {
    private static final String ENCRYPTION_LABEL = "elder:encryption:v1";
    private final DataKeyRing keys;
    private final byte[] indexKey;
    private final ObjectMapper json;

    /** 使用公共密钥环派生档案加密及检索密钥，密钥校验和轮换规则由密钥环统一负责。 */
    public ElderCrypto(DataKeyRing keys, ObjectMapper json) {
        this.keys = keys;
        this.json = json;
        indexKey = keys.indexKey("elder:index:v1");
    }

    /** 加密完整资料，将社区和档案编号绑定为认证附加数据，防止密文跨档案替换。 */
    public String encrypt(long id, long communityId, ElderProfileData profile) {
        try { return keys.seal(ENCRYPTION_LABEL, aad(id, communityId), json.writeValueAsBytes(profile)); }
        catch (Exception e) { throw new IllegalStateException("老人档案加密失败"); }
    }

    /** 验证密文完整性和档案归属后解密，失败时不返回部分数据。 */
    public ElderProfileData decrypt(long id, long communityId, String payload) {
        try { return json.readValue(keys.open(ENCRYPTION_LABEL, aad(id, communityId), payload), ElderProfileData.class); }
        catch (Exception e) { throw new IllegalStateException("老人档案解密失败"); }
    }

    /** 生成社区内的精确检索摘要，避免保存可检索字段明文。 */
    public String index(long communityId, String value) {
        return Base64.getEncoder().encodeToString(DataKeyRing.hmac(indexKey, communityId + ":" + value));
    }

    /** 对规范化资料计算请求摘要，用于判断重复建档请求是否一致。 */
    public String fingerprint(long communityId, ElderProfileData profile) {
        try { return index(communityId, json.writeValueAsString(profile)); }
        catch (Exception e) { throw new IllegalStateException("老人档案请求摘要计算失败"); }
    }

    /** 绑定社区和档案编号的附加数据，保持历史密文格式。 */
    private static String aad(long id, long communityId) {
        return "elder:v1:" + communityId + ":" + id;
    }
}
