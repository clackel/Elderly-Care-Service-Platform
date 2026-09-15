package com.elderlycare.platform.elder;

import com.elderlycare.platform.elder.domain.ElderProfileData;
import com.elderlycare.platform.elder.service.ElderCrypto;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class ElderCryptoTest {
    @Test
    @DisplayName("正式环境拒绝缺失、非Base64和长度错误的密钥")
    void rejectsInvalidKeys() {
        var environment = new MockEnvironment();
        var json = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        for (String key : new String[]{"", "not-base64!", "YQ=="}) {
            assertThatThrownBy(() -> new ElderCrypto(key, environment, json))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ELDER_DATA_KEY");
        }
    }

    @Test
    @DisplayName("随机密文可由同密钥恢复，跨档案及错误密钥解密失败")
    void authenticatedEncryptionAndStableIndexes() {
        var json = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        String key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
        var crypto = new ElderCrypto(key,new MockEnvironment(),json);
        var restarted = new ElderCrypto(key,new MockEnvironment(),json);
        var data = new ElderProfileData("加密测试",ElderProfileData.Gender.UNKNOWN,LocalDate.of(1950,1,1),
                null,"合成地址",ElderProfileData.LivingArrangement.UNKNOWN,"联系人","13800138000","子女",null);
        String first = crypto.encrypt(1,1,data);
        assertThat(crypto.encrypt(1,1,data)).isNotEqualTo(first);
        assertThat(restarted.decrypt(1,1,first)).isEqualTo(data);
        assertThat(crypto.index(1,"测试")).isEqualTo(restarted.index(1,"测试")).isNotEqualTo(crypto.index(2,"测试"));
        assertThatThrownBy(() -> crypto.decrypt(2,1,first)).hasMessage("老人档案解密失败");
        assertThatThrownBy(() -> crypto.decrypt(1,2,first)).hasMessage("老人档案解密失败");
        var wrong = new ElderCrypto("YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",new MockEnvironment(),json);
        assertThatThrownBy(() -> wrong.decrypt(1,1,first)).hasMessage("老人档案解密失败");
        assertThat(data.toString()).doesNotContain("合成地址","13800138000");
    }
}
