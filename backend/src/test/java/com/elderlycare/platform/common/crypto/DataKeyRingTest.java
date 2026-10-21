package com.elderlycare.platform.common.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DataKeyRingTest {
    private static final String KEY_1 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String KEY_2 = "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=";

    /** 切换活动密钥后新密文带密钥编号，旧v1密文仍可解密，检索密钥不随活动密钥变化。 */
    @Test void rotatesActiveKeyWithoutBreakingLegacyCiphertextOrIndexes() throws Exception {
        var legacy = new DataKeyRing(KEY_1, Map.of(), "1", false);
        var rotated = new DataKeyRing(KEY_1, Map.of("2", KEY_2), "2", false);
        byte[] plain = "合成资料".getBytes(StandardCharsets.UTF_8);
        String old = legacy.seal("elder:encryption:v1", "elder:v1:1:1", plain);
        String fresh = rotated.seal("elder:encryption:v1", "elder:v1:1:1", plain);
        assertThat(old).startsWith("v1:");
        assertThat(fresh).startsWith("v2:2:");
        assertThat(rotated.open("elder:encryption:v1", "elder:v1:1:1", old)).isEqualTo(plain);
        assertThat(rotated.open("elder:encryption:v1", "elder:v1:1:1", fresh)).isEqualTo(plain);
        assertThat(rotated.usesActiveKey(old)).isFalse();
        assertThat(rotated.usesActiveKey(fresh)).isTrue();
        assertThat(rotated.indexKey("elder:index:v1")).isEqualTo(legacy.indexKey("elder:index:v1"));
        assertThatThrownBy(() -> legacy.open("elder:encryption:v1", "elder:v1:1:1", fresh))
                .hasMessage("密文使用的密钥未配置");
        assertThatThrownBy(() -> rotated.open("elder:encryption:v1", "elder:v1:1:2", fresh))
                .isInstanceOf(java.security.GeneralSecurityException.class);
    }

    /** 活动密钥未配置、保留编号重复或非法编号时拒绝启动，错误消息不包含密钥内容。 */
    @Test void rejectsInvalidKeyConfiguration() {
        assertThatThrownBy(() -> new DataKeyRing(KEY_1, Map.of(), "2", false))
                .hasMessageContaining("app.crypto.active-key-id");
        assertThatThrownBy(() -> new DataKeyRing(KEY_1, Map.of("1", KEY_2), "1", false))
                .hasMessageContaining("保留编号1");
        assertThatThrownBy(() -> new DataKeyRing(KEY_1, Map.of("2", "YQ=="), "1", false))
                .hasMessage("app.crypto.keys.2解码后必须为32字节");
        assertThatThrownBy(() -> new DataKeyRing("", Map.of(), "1", false))
                .hasMessageContaining("ELDER_DATA_KEY").hasMessageNotContaining(KEY_1);
    }
}
