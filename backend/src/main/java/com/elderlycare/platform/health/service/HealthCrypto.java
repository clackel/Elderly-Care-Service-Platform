package com.elderlycare.platform.health.service;

import com.elderlycare.platform.common.crypto.DataKeyRing;
import com.elderlycare.platform.health.api.HealthResponses.Payload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Base64;

/** 健康域独立派生加密和摘要密钥，密文绑定社区、老人、记录及版本。 */
@Component
public class HealthCrypto {
    private static final String ENCRYPTION_LABEL = "health:encryption:v1";
    private final DataKeyRing keys;
    private final byte[] hashKey;
    private final ObjectMapper json;
    /** 从公共密钥环派生健康专用密钥，不改变档案和预约格式。 */
    public HealthCrypto(DataKeyRing keys, ObjectMapper json) {
        this.keys = keys;
        this.json = json;
        hashKey = keys.indexKey("health:request:v1");
    }
    /** 使用HMAC摘要规范化请求，避免小范围测量值被无密钥枚举。 */
    public String hash(String purpose, Object value) {
        try { return Base64.getEncoder().encodeToString(DataKeyRing.hmac(hashKey, purpose + ":" + json.writeValueAsString(value))); }
        catch (Exception e) { throw new IllegalStateException("健康请求摘要计算失败"); }
    }
    /** 用随机IV加密每一版固定测量和原因。 */
    public String encrypt(long community, long elder, long record, long version, Payload payload) {
        try { return keys.seal(ENCRYPTION_LABEL, aad(community, elder, record, version), json.writeValueAsBytes(payload)); }
        catch (Exception e) { throw new IllegalStateException("健康记录加密失败"); }
    }
    /** 验证全部对象上下文后还原健康正文，替换或篡改均拒绝。 */
    public Payload decrypt(long community, long elder, long record, long version, String payload) {
        try { return json.readValue(keys.open(ENCRYPTION_LABEL, aad(community, elder, record, version), payload), Payload.class); }
        catch (Exception e) { throw new IllegalStateException("健康记录解密失败"); }
    }
    /** 绑定完整健康记录身份及不可变版本的附加数据，保持历史密文格式。 */
    private static String aad(long community, long elder, long record, long version) {
        return "health:v1:" + community + ":" + elder + ":" + record + ":" + version;
    }
}
