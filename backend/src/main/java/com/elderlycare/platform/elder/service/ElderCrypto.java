package com.elderlycare.platform.elder.service;

import com.elderlycare.platform.elder.domain.ElderProfileData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ElderCrypto {
    private final byte[] encryptionKey;
    private final byte[] indexKey;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();

    /** 校验主密钥，并派生加密及检索密钥；正式环境缺少配置时拒绝启动。 */
    public ElderCrypto(@Value("${app.elder.encryption-key:}") String configuredKey,
                       Environment environment, ObjectMapper json) {
        this.json = json;
        // 固定公开密钥仅用于dev环境的合成数据。
        String key = configuredKey;
        if (key.isBlank() && environment.acceptsProfiles(Profiles.of("dev"))) {
            key = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        }
        byte[] master;
        try { master = Base64.getDecoder().decode(key); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("ELDER_DATA_KEY必须使用Base64编码", e); }
        // 使用不同派生密钥隔离资料加密和检索索引。
        if (master.length != 32) {
            throw new IllegalStateException("ELDER_DATA_KEY解码后必须为32字节");
        }
        encryptionKey = mac(master, "elder:encryption:v1");
        indexKey = mac(master, "elder:index:v1");
    }

    /** 加密完整资料，将社区和档案编号绑定为认证附加数据，防止密文跨档案替换。 */
    public String encrypt(long id, long communityId, ElderProfileData profile) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, id, communityId);
            byte[] encrypted = cipher.doFinal(json.writeValueAsBytes(profile));
            return "v1:" + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (Exception e) { throw new IllegalStateException("老人档案加密失败"); }
    }

    /** 验证密文完整性和档案归属后解密，失败时不返回部分数据。 */
    public ElderProfileData decrypt(long id, long communityId, String payload) {
        try {
            if (!payload.startsWith("v1:")) throw new IllegalArgumentException("老人档案密文格式无效");
            byte[] packed = Base64.getDecoder().decode(payload.substring(3));
            if (packed.length < 28) throw new IllegalArgumentException("老人档案密文格式无效");
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] nonce = new byte[12]; buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            return json.readValue(cipher(Cipher.DECRYPT_MODE, nonce, id, communityId).doFinal(encrypted), ElderProfileData.class);
        } catch (Exception e) { throw new IllegalStateException("老人档案解密失败"); }
    }

    /** 生成社区内的精确检索摘要，避免保存可检索字段明文。 */
    public String index(long communityId, String value) {
        return Base64.getEncoder().encodeToString(mac(indexKey, communityId + ":" + value));
    }

    /** 对规范化资料计算请求摘要，用于判断重复建档请求是否一致。 */
    public String fingerprint(long communityId, ElderProfileData profile) {
        try { return index(communityId, json.writeValueAsString(profile)); }
        catch (Exception e) { throw new IllegalStateException("老人档案请求摘要计算失败"); }
    }

    /** 初始化AES-GCM并绑定社区和档案编号。 */
    private Cipher cipher(int mode, byte[] nonce, long id, long communityId) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("elder:v1:" + communityId + ":" + id).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    /** 使用HMAC生成摘要，失败时抛出不包含输入数据的中文异常。 */
    private static byte[] mac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("老人档案密钥派生失败"); }
    }
}
