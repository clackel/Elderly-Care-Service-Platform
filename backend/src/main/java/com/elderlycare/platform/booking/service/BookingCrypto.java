package com.elderlycare.platform.booking.service;

import com.elderlycare.platform.common.crypto.DataKeyRing;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.util.Base64;

/** 预约资料与授权依据使用独立派生密钥，密文绑定用途、社区及对象编号。 */
@Component
public class BookingCrypto {
    private static final String ENCRYPTION_LABEL = "booking:encryption:v1";
    private final DataKeyRing keys;
    private final byte[] hashKey;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();

    /** 从公共密钥环派生预约专用密钥；主密钥校验及开发密钥规则由密钥环负责。 */
    public BookingCrypto(DataKeyRing keys, ObjectMapper json) {
        this.keys = keys;
        this.json = json;
        hashKey = keys.indexKey("booking:hash:v1");
    }

    /** 生成不可逆的带密钥摘要，支持幂等内容、微信身份和一次性开通码。 */
    public String hash(String purpose, Object value) {
        return Base64.getEncoder().encodeToString(DataKeyRing.hmac(hashKey, purpose + ":" + encode(value)));
    }

    /** 序列化无敏感信息的服务快照；失败时不输出原文。 */
    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("预约资料序列化失败"); }
    }

    /** 按明确类型还原快照，格式无效时拒绝返回。 */
    public <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (Exception e) { throw new IllegalStateException("预约资料读取失败"); }
    }

    /** 加密个人资料，返回携带版本、密钥编号及随机IV的认证密文。 */
    public String encrypt(String purpose, long community, long id, Object value) {
        try { return keys.seal(ENCRYPTION_LABEL, aad(purpose, community, id), json.writeValueAsBytes(value)); }
        catch (Exception e) { throw new IllegalStateException("预约资料加密失败"); }
    }

    /** 验证用途和归属后解密为指定类型，失败不暴露任何正文。 */
    public <T> T decrypt(String purpose, long community, long id, String value, Class<T> type) {
        try { return json.readValue(keys.open(ENCRYPTION_LABEL, aad(purpose, community, id), value), type); }
        catch (Exception e) { throw new IllegalStateException("预约资料解密失败"); }
    }

    /** 生成32字节随机开通凭证，只在返回给核验人员时展示原值。 */
    public String token() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 绑定用途、社区和对象编号的附加数据，保持历史密文格式。 */
    private static String aad(String purpose, long community, long id) {
        return "booking:v1:" + purpose + ":" + community + ":" + id;
    }
}
