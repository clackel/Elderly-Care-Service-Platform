package com.elderlycare.platform.health.service;

import com.elderlycare.platform.health.api.HealthResponses.Payload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** 健康域独立派生加密和摘要密钥，密文绑定社区、老人、记录及版本。 */
@Component
public class HealthCrypto {
    private final byte[] key;
    private final byte[] hashKey;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();
    /** 从现有主密钥派生健康专用密钥，不改变档案和预约格式。 */
    public HealthCrypto(@Value("${app.elder.encryption-key:}") String configured, Environment env, ObjectMapper json) {
        this.json = json;
        if (configured.isBlank() && env.acceptsProfiles(Profiles.of("dev")))
            configured = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        byte[] master;
        try { master = Base64.getDecoder().decode(configured); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("健康数据密钥必须使用Base64编码"); }
        if (master.length != 32) throw new IllegalStateException("健康数据密钥解码后必须为32字节");
        key = mac(master, "health:encryption:v1");
        hashKey = mac(master, "health:request:v1");
    }
    /** 使用HMAC摘要规范化请求，避免小范围测量值被无密钥枚举。 */
    public String hash(String purpose, Object value) {
        try { return Base64.getEncoder().encodeToString(mac(hashKey, purpose + ":" + json.writeValueAsString(value))); }
        catch (Exception e) { throw new IllegalStateException("健康请求摘要计算失败"); }
    }
    /** 用随机IV加密每一版固定测量和原因。 */
    public String encrypt(long community, long elder, long record, long version, Payload payload) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            byte[] data = cipher(Cipher.ENCRYPT_MODE, community, elder, record, version, nonce).doFinal(json.writeValueAsBytes(payload));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + data.length).put(nonce).put(data).array());
        } catch (Exception e) { throw new IllegalStateException("健康记录加密失败"); }
    }
    /** 验证全部对象上下文后还原健康正文，替换或篡改均拒绝。 */
    public Payload decrypt(long community, long elder, long record, long version, String payload) {
        try {
            if (!payload.startsWith("v1:")) throw new IllegalArgumentException("健康密文版本无效");
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(payload.substring(3)));
            byte[] nonce = new byte[12]; buffer.get(nonce);
            byte[] data = new byte[buffer.remaining()]; buffer.get(data);
            return json.readValue(cipher(Cipher.DECRYPT_MODE, community, elder, record, version, nonce).doFinal(data), Payload.class);
        } catch (Exception e) { throw new IllegalStateException("健康记录解密失败"); }
    }
    /** 初始化AES-GCM并绑定完整健康记录身份及不可变版本。 */
    private Cipher cipher(int mode, long community, long elder, long record, long version, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("health:v1:" + community + ":" + elder + ":" + record + ":" + version).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
    /** 计算带密钥摘要；错误不包含任何输入。 */
    private static byte[] mac(byte[] key, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("健康密钥派生失败"); }
    }
}

