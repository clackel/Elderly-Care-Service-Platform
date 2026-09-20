package com.elderlycare.platform.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** 预约资料与授权依据使用独立派生密钥，密文绑定用途、社区及对象编号。 */
@Component
public class BookingCrypto {
    private final byte[] key;
    private final byte[] hashKey;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();

    /** 从现有数据主密钥派生预约专用密钥；公开开发密钥只允许dev合成数据。 */
    public BookingCrypto(@Value("${app.elder.encryption-key:}") String configured, Environment env, ObjectMapper json) {
        this.json = json;
        if (configured.isBlank() && env.acceptsProfiles(Profiles.of("dev")))
            configured = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        byte[] master;
        try { master = Base64.getDecoder().decode(configured); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("预约数据密钥必须使用Base64编码"); }
        if (master.length != 32) throw new IllegalStateException("预约数据密钥解码后必须为32字节");
        key = mac(master, "booking:encryption:v1");
        hashKey = mac(master, "booking:hash:v1");
    }

    /** 生成不可逆的带密钥摘要，支持幂等内容、微信身份和一次性开通码。 */
    public String hash(String purpose, Object value) {
        return Base64.getEncoder().encodeToString(mac(hashKey, purpose + ":" + encode(value)));
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

    /** 加密个人资料，返回携带版本及随机IV的认证密文。 */
    public String encrypt(String purpose, long community, long id, Object value) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            byte[] data = cipher(Cipher.ENCRYPT_MODE, purpose, community, id, nonce).doFinal(json.writeValueAsBytes(value));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + data.length).put(nonce).put(data).array());
        } catch (Exception e) { throw new IllegalStateException("预约资料加密失败"); }
    }

    /** 验证用途和归属后解密为指定类型，失败不暴露任何正文。 */
    public <T> T decrypt(String purpose, long community, long id, String value, Class<T> type) {
        try {
            if (!value.startsWith("v1:")) throw new IllegalArgumentException("预约密文版本无效");
            ByteBuffer data = ByteBuffer.wrap(Base64.getDecoder().decode(value.substring(3)));
            byte[] nonce = new byte[12]; data.get(nonce);
            byte[] encrypted = new byte[data.remaining()]; data.get(encrypted);
            return json.readValue(cipher(Cipher.DECRYPT_MODE, purpose, community, id, nonce).doFinal(encrypted), type);
        } catch (Exception e) { throw new IllegalStateException("预约资料解密失败"); }
    }

    /** 生成32字节随机开通凭证，只在返回给核验人员时展示原值。 */
    public String token() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 绑定对象附加数据并初始化AES-GCM。 */
    private Cipher cipher(int mode, String purpose, long community, long id, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("booking:v1:" + purpose + ":" + community + ":" + id).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    /** 使用不同用途的HMAC摘要，异常不得包含输入内容。 */
    private static byte[] mac(byte[] key, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("预约资料摘要计算失败"); }
    }
}

