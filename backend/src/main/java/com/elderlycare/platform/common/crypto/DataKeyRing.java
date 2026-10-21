package com.elderlycare.platform.common.crypto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 业务数据主密钥环：统一完成主密钥校验、按用途派生子密钥及AES-GCM认证加密。
 *
 * <p>密钥1来自既有的{@code app.elder.encryption-key}，其余密钥来自{@code app.crypto.keys.<编号>}；
 * {@code app.crypto.active-key-id}决定新密文使用的密钥。密钥1写出的密文保持原有{@code v1:}格式，
 * 其他密钥写出{@code v2:<编号>:}格式，解密时按前缀选择密钥，因此切换活动密钥后旧数据仍可读取。
 * 检索索引和请求摘要始终由密钥1派生，更换密钥1需要重建索引，不在本组件职责内。</p>
 */
@Component
public class DataKeyRing {
    /** 既有主密钥编号，同时负责派生检索与摘要密钥。 */
    public static final String LEGACY_KEY_ID = "1";
    // 固定公开密钥仅用于dev环境的合成数据。
    private static final String DEV_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final Pattern KEY_ID = Pattern.compile("[0-9a-z]{1,16}");
    private static final int NONCE_BYTES = 12;

    private final Map<String, byte[]> masters;
    private final String activeKeyId;
    private final Map<String, byte[]> derived = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** 从运行配置读取全部主密钥；正式环境缺少密钥1或活动密钥不存在时拒绝启动。 */
    @Autowired
    public DataKeyRing(Environment environment) {
        this(environment.getProperty("app.elder.encryption-key", ""),
                Binder.get(environment).bind("app.crypto.keys", Bindable.mapOf(String.class, String.class)).orElse(Map.of()),
                environment.getProperty("app.crypto.active-key-id", LEGACY_KEY_ID),
                environment.acceptsProfiles(Profiles.of("dev")));
    }

    /**
     * 使用显式配置构建密钥环，供启动绑定和最小单元测试复用。
     *
     * @param legacyKey 密钥1的Base64原文，dev环境为空时使用公开开发密钥
     * @param extraKeys 其他主密钥，键为小写字母或数字组成的编号
     * @param activeKeyId 新写入密文使用的主密钥编号
     * @param dev 是否允许使用公开开发密钥
     */
    public DataKeyRing(String legacyKey, Map<String, String> extraKeys, String activeKeyId, boolean dev) {
        var keys = new LinkedHashMap<String, byte[]>();
        String legacy = legacyKey == null ? "" : legacyKey;
        if (legacy.isBlank() && dev) legacy = DEV_KEY;
        keys.put(LEGACY_KEY_ID, decodeMaster("ELDER_DATA_KEY", legacy));
        extraKeys.forEach((id, value) -> {
            if (!KEY_ID.matcher(id).matches() || LEGACY_KEY_ID.equals(id))
                throw new IllegalStateException("app.crypto.keys的编号必须为1至16位小写字母或数字，且不能使用保留编号1");
            keys.put(id, decodeMaster("app.crypto.keys." + id, value));
        });
        String active = activeKeyId == null || activeKeyId.isBlank() ? LEGACY_KEY_ID : activeKeyId.trim();
        if (!keys.containsKey(active))
            throw new IllegalStateException("app.crypto.active-key-id指向的密钥未配置，请先在app.crypto.keys中添加该编号");
        this.masters = Map.copyOf(keys);
        this.activeKeyId = active;
    }

    /** 返回由密钥1按用途派生的摘要密钥，用于检索索引、幂等摘要和限流计数键。 */
    public byte[] indexKey(String label) {
        return derive(LEGACY_KEY_ID, label);
    }

    /** 计算带密钥的HMAC-SHA256摘要；失败时抛出不包含输入内容的异常。 */
    public static byte[] hmac(byte[] key, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("数据摘要计算失败");
        }
    }

    /**
     * 使用活动主密钥加密，随机IV并绑定认证附加数据。
     *
     * @param label 子密钥用途，各业务域保持既有标签以兼容历史密文
     * @param aad 绑定社区、对象编号等上下文的附加数据
     * @return 带格式版本和密钥编号的Base64密文
     */
    public String seal(String label, String aad, byte[] plain) throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] data = cipher(Cipher.ENCRYPT_MODE, activeKeyId, label, aad, nonce).doFinal(plain);
        String body = Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(nonce.length + data.length).put(nonce).put(data).array());
        return LEGACY_KEY_ID.equals(activeKeyId) ? "v1:" + body : "v2:" + activeKeyId + ":" + body;
    }

    /** 按密文前缀选择主密钥并验证附加数据后解密；格式、密钥或认证失败均抛出异常。 */
    public byte[] open(String label, String aad, String payload) throws GeneralSecurityException {
        String keyId;
        String body;
        if (payload.startsWith("v1:")) {
            keyId = LEGACY_KEY_ID;
            body = payload.substring(3);
        } else if (payload.startsWith("v2:") && payload.indexOf(':', 3) > 3) {
            int split = payload.indexOf(':', 3);
            keyId = payload.substring(3, split);
            body = payload.substring(split + 1);
        } else {
            throw new GeneralSecurityException("密文格式无效");
        }
        if (!masters.containsKey(keyId)) throw new GeneralSecurityException("密文使用的密钥未配置");
        byte[] packed = Base64.getDecoder().decode(body);
        if (packed.length < NONCE_BYTES + 16) throw new GeneralSecurityException("密文格式无效");
        ByteBuffer buffer = ByteBuffer.wrap(packed);
        byte[] nonce = new byte[NONCE_BYTES];
        buffer.get(nonce);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        return cipher(Cipher.DECRYPT_MODE, keyId, label, aad, nonce).doFinal(encrypted);
    }

    /** 判断密文是否已由活动密钥写出，供后续重加密任务筛选旧密文。 */
    public boolean usesActiveKey(String payload) {
        return LEGACY_KEY_ID.equals(activeKeyId) ? payload.startsWith("v1:") : payload.startsWith("v2:" + activeKeyId + ":");
    }

    /** 初始化绑定子密钥和附加数据的AES-GCM。 */
    private Cipher cipher(int mode, String keyId, String label, String aad, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(derive(keyId, label), "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    /** 以主密钥对用途标签做HMAC派生子密钥，并按编号和用途缓存。 */
    private byte[] derive(String keyId, String label) {
        return derived.computeIfAbsent(keyId + "\n" + label, ignored -> hmac(masters.get(keyId), label));
    }

    /** 校验Base64编码及32字节长度；错误消息只包含配置名，不包含密钥内容。 */
    private static byte[] decodeMaster(String name, String value) {
        byte[] master;
        try {
            master = Base64.getDecoder().decode(value == null ? "" : value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + "必须使用Base64编码");
        }
        if (master.length != 32) throw new IllegalStateException(name + "解码后必须为32字节");
        return master;
    }
}
