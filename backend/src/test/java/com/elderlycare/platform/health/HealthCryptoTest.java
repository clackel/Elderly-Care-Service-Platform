package com.elderlycare.platform.health;

import com.elderlycare.platform.health.api.*;
import com.elderlycare.platform.health.domain.HealthTypes.*;
import com.elderlycare.platform.health.service.HealthCrypto;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class HealthCryptoTest {
    /** 健康密文不能移到另一个社区、老人、记录或版本，随机IV避免重复密文。 */
    @Test void bindsEveryObjectDimensionAndUsesRandomNonce() {
        var env = new MockEnvironment(); env.setActiveProfiles("dev");
        var crypto = new HealthCrypto("", env, JsonMapper.builder().findAndAddModules().build());
        var payload = new HealthResponses.Payload(new HealthRequests.Measurement(Type.HEART_RATE,
                Instant.parse("2020-01-01T00:00:00Z"), null, null, 70, null, null, null), "合成更正原因");
        String cipher = crypto.encrypt(1, 2, 3, 1, payload);
        assertThat(crypto.decrypt(1, 2, 3, 1, cipher)).isEqualTo(payload);
        assertThat(cipher).isNotEqualTo(crypto.encrypt(1, 2, 3, 1, payload)).doesNotContain("合成");
        assertThatThrownBy(() -> crypto.decrypt(9, 2, 3, 1, cipher)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(1, 9, 3, 1, cipher)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(1, 2, 9, 1, cipher)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(1, 2, 3, 2, cipher)).isInstanceOf(IllegalStateException.class);
        assertThat(crypto.hash("CREATE", payload)).isEqualTo(crypto.hash("CREATE", payload)).isNotEqualTo(crypto.hash("CORRECT", payload));
    }
}

