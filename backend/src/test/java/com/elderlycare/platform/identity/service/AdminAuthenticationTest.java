package com.elderlycare.platform.identity.service;

import com.elderlycare.platform.booking.service.BookingCrypto;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.identity.domain.Community;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.elderlycare.platform.identity.mapper.UserAccountMapper;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAuthenticationTest {
    @Test void accountLimitCoversDifferentAddressesAndExpiresAtWindowBoundary() {
        var crypto = crypto(); var clock = mock(Clock.class); var now = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        var throttle = new AdminLoginThrottle(crypto, clock);
        for (int i = 0; i < 10; i++) throttle.check("peer-" + i, " TEST.ADMIN ");
        assertThatThrownBy(() -> throttle.check("another-peer", "test.admin"))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo("LOGIN_RATE_LIMITED"));
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertThatCode(() -> throttle.check("another-peer", "test.admin")).doesNotThrowAnyException();
    }

    @Test void sourceLimitCoversDifferentAccounts() {
        var throttle = new AdminLoginThrottle(crypto(), Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC));
        for (int i = 0; i < 20; i++) throttle.check("one-peer", "account-" + i);
        assertThatThrownBy(() -> throttle.check("one-peer", "new-account")).isInstanceOf(BusinessException.class);
    }

    @Test void passwordPolicyChecksUtf8BytesBeforeBcrypt() {
        assertThatCode(() -> AdminPasswordPolicy.validate("a".repeat(64))).doesNotThrowAnyException();
        assertThatCode(() -> AdminPasswordPolicy.validate("测".repeat(24))).doesNotThrowAnyException();
        assertThatThrownBy(() -> AdminPasswordPolicy.validate("测".repeat(25))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AdminPasswordPolicy.validate("short")).isInstanceOf(BusinessException.class);
    }

    @Test void mobileAndUnavailableCommunityCannotUsePasswordLogin() {
        var mapper = mock(UserAccountMapper.class); var communities = mock(CommunityMapper.class);
        var service = new IdentityService(mapper, communities); var account = new UserAccount();
        account.setId(1L); account.setUsername("test.user"); account.setRole("FAMILY"); account.setStatus("ACTIVE");
        account.setCommunityId(1L); account.setPasswordHash("synthetic-hash");
        when(mapper.selectOne(any())).thenReturn(account);
        assertThatThrownBy(() -> service.loadUserByUsername("test.user")).isInstanceOf(UsernameNotFoundException.class);
        account.setRole("PLATFORM_ADMIN");
        assertThatThrownBy(() -> service.loadUserByUsername("test.user")).isInstanceOf(UsernameNotFoundException.class);
        when(communities.findActiveById(1L)).thenReturn(new Community(1L, "合成社区"));
        assertThat(service.loadUserByUsername(" TEST.USER ")).isInstanceOf(AccountSessions.LoginUser.class);
    }

    /** 以可比较的合成摘要替代密钥设施，仅测试计数及归一规则。 */
    private BookingCrypto crypto() {
        var crypto = mock(BookingCrypto.class);
        when(crypto.hash(anyString(), any())).thenAnswer(call -> call.getArgument(0) + ":" + call.getArgument(1));
        return crypto;
    }
}
