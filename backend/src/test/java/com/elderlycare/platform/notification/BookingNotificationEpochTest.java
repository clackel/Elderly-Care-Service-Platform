package com.elderlycare.platform.notification;

import com.elderlycare.platform.booking.api.BookingRequests;
import com.elderlycare.platform.booking.domain.GrantRow;
import com.elderlycare.platform.booking.mapper.BindingMapper;
import com.elderlycare.platform.booking.mapper.GrantMapper;
import com.elderlycare.platform.booking.service.BookingAccess;
import com.elderlycare.platform.booking.service.BookingCrypto;
import com.elderlycare.platform.consent.service.BookingConsentService;
import com.elderlycare.platform.elder.domain.ElderProfileRow;
import com.elderlycare.platform.elder.mapper.ElderProfileMapper;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.UserAccountMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 预约授权通知周期独立于普通资料版本，防止撤销或到期后的旧消息复活。 */
class BookingNotificationEpochTest {
    private final BookingAccess access = mock(BookingAccess.class);
    private final GrantMapper grants = mock(GrantMapper.class);
    private final UserAccountMapper accounts = mock(UserAccountMapper.class);
    private final ElderProfileMapper elders = mock(ElderProfileMapper.class);
    private final BookingConsentService service = new BookingConsentService(access, mock(BookingCrypto.class),
            mock(BindingMapper.class), grants, accounts, elders);

    /** 有效期内续期和变更代办权限保留原消息周期，失效后重新授权开始新周期。 */
    @Test void ordinaryRenewalKeepsEpochButExpiredGrantStartsAnotherEpoch() {
        var row = prepare();
        service.grant(null, input(3L)); assertThat(row.notificationEpoch).isEqualTo(5);
        row.expiresAt = Instant.now().minusSeconds(60);
        service.grant(null, input(4L)); assertThat(row.notificationEpoch).isEqualTo(6);
    }

    /** 撤销立即推进周期，恢复同一授权记录也不会让旧消息重新可见。 */
    @Test void revokeAndRegrantCannotRestorePreviousNotificationEpoch() {
        var row = prepare();
        service.revoke(null, 7L, 3L); assertThat(row.revoked).isTrue(); assertThat(row.notificationEpoch).isEqualTo(6);
        service.grant(null, input(4L)); assertThat(row.revoked).isFalse(); assertThat(row.notificationEpoch).isEqualTo(7);
    }

    /** 构造本社区合成家属授权，禁止使用真实身份资料。 */
    private GrantRow prepare() {
        var actor = new UserAccount(); actor.setId(1L); actor.setCommunityId(9L); actor.setRole("PLATFORM_ADMIN");
        when(access.writer(null)).thenReturn(actor); when(access.isOperator(actor)).thenReturn(true);
        var family = new UserAccount(); family.setId(2L); family.setCommunityId(9L); family.setRole("FAMILY"); family.setStatus("ACTIVE");
        when(accounts.selectById(2L)).thenReturn(family);
        var elder = new ElderProfileRow(); elder.setId(6L); elder.setCommunityId(9L); elder.setStatus("ACTIVE");
        when(elders.find(9L, 6L)).thenReturn(elder);
        var grant = new GrantRow(); grant.id = 7L; grant.communityId = 9L; grant.familyId = 2L; grant.elderId = 6L;
        grant.version = 3L; grant.notificationEpoch = 5; grant.canBook = false; grant.revoked = false;
        grant.expiresAt = Instant.now().plusSeconds(1800);
        when(grants.selectOne(any())).thenReturn(grant); when(grants.selectById(7L)).thenReturn(grant);
        return grant;
    }

    /** 仅用当前时间附近的合成授权期限，不依赖真实业务安排。 */
    private BookingRequests.Grant input(long version) {
        return new BookingRequests.Grant(6L, 2L, version, false, Instant.now().plusSeconds(3600), "合成同意依据");
    }
}
