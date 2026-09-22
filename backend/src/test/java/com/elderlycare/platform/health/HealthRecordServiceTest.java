package com.elderlycare.platform.health;

import com.elderlycare.platform.health.api.*;
import com.elderlycare.platform.health.domain.*;
import com.elderlycare.platform.health.domain.HealthTypes.*;
import com.elderlycare.platform.health.mapper.*;
import com.elderlycare.platform.health.service.*;
import com.elderlycare.platform.elder.domain.ElderProfileRow;
import com.elderlycare.platform.elder.service.ElderCrypto;
import com.elderlycare.platform.identity.domain.UserAccount;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class HealthRecordServiceTest {
    private final HealthAccess access = mock(HealthAccess.class);
    private final HealthQueries queries = mock(HealthQueries.class);
    private final HealthRecordMapper records = mock(HealthRecordMapper.class);
    private final HealthRevisionMapper revisions = mock(HealthRevisionMapper.class);
    private final HealthCrypto crypto = mock(HealthCrypto.class);
    private final HealthMutations mutations = mock(HealthMutations.class);
    private final HealthRecordService service = new HealthRecordService(access, queries, records, revisions, crypto, new HealthRules(), mutations, mock(ElderCrypto.class));
    /** 构造合成的社区代录记录。 */
    private HealthRecordRow record() {
        var row = new HealthRecordRow(); row.id = 3L; row.elderId = 2L; row.communityId = 1L; row.version = 1L;
        row.originalActorId = 5L; row.type = "HEART_RATE"; row.status = "ACTIVE"; row.entryMode = "COMMUNITY_ASSIST"; row.ownerTakenOver = false;
        return row;
    }
    /** 返回稳定合成测量，避免依赖当前时间。 */
    private HealthRequests.Measurement measurement() {
        return new HealthRequests.Measurement(Type.HEART_RATE, Instant.parse("2020-01-01T00:00:00Z"), null, null, 72, null, null, null);
    }
    /** 老人更正触发接管且保留原录入人；版本冲突不会追加历史。 */
    @Test void ownerCorrectionTakesOverWithoutChangingOrigin() {
        var actor = new UserAccount(); actor.setId(6L); actor.setRole("ELDER");
        var row = record(); when(access.writer(null)).thenReturn(actor); when(access.record(actor, 3)).thenReturn(row);
        when(queries.advance(any(), eq(1L))).thenReturn(1);
        service.correct(null, 3, new HealthRequests.Correct(UUID.randomUUID(), 1L, measurement(), "合成更正"));
        assertThat(row.ownerTakenOver).isTrue(); assertThat(row.originalActorId).isEqualTo(5); assertThat(row.version).isEqualTo(2);
        verify(revisions).insert(any(HealthRevisionRow.class)); verify(access).audit(actor, 3, "CORRECT");
        reset(revisions);
        assertThatThrownBy(() -> service.correct(null, 3, new HealthRequests.Correct(UUID.randomUUID(), 1L, measurement(), "冲突")))
                .isInstanceOf(com.elderlycare.platform.common.api.BusinessException.class);
        verifyNoInteractions(revisions);
    }
    /** 撤销或接管导致对象不再可见时，不能通过幂等结果绕过当前权限。 */
    @Test void authorizationRunsBeforeIdempotentReplay() {
        var actor = new UserAccount(); when(access.writer(null)).thenReturn(actor);
        when(access.record(actor, 3)).thenThrow(HealthAccess.missing());
        assertThatThrownBy(() -> service.correct(null, 3, new HealthRequests.Correct(UUID.randomUUID(), 1L, measurement(), "合成更正")))
                .isInstanceOf(com.elderlycare.platform.common.api.BusinessException.class);
        verifyNoInteractions(mutations, revisions, queries);
    }
    /** 旧请求重试返回原结果版本，不再次更正或追加历史。 */
    @Test void replayReturnsOriginalResultBeforeVersionCheck() {
        var actor = new UserAccount(); var row = record(); row.version = 4L;
        when(access.writer(null)).thenReturn(actor); when(access.record(actor, 3)).thenReturn(row);
        when(mutations.hash(any(), anyString(), any())).thenReturn("hash");
        var result = new HealthResponses.Mutation("3", 2);
        when(mutations.replay(eq(actor), eq("CORRECT"), any(), eq("hash"))).thenReturn(result);
        assertThat(service.correct(null, 3, new HealthRequests.Correct(UUID.randomUUID(), 1L, measurement(), "合成更正"))).isEqualTo(result);
        verifyNoInteractions(revisions, queries);
    }
    /** 趋势读取超过硬上限时拒绝，不解密并返回静默截断的点。 */
    @Test void excessiveTrendPointsAreRejectedBeforeDecryption() {
        var actor = new UserAccount(); when(access.actor(null)).thenReturn(actor);
        when(access.elder(actor, 2)).thenReturn(new ElderProfileRow());
        when(queries.records(any(), eq(2L), any(), any(), isNull(), any(), any(), any(), eq(0L), eq(2001), eq(true)))
                .thenReturn(java.util.Collections.nCopies(2001, record()));
        assertThatThrownBy(() -> service.trends(null, 2, Type.HEART_RATE, null, Instant.EPOCH, Instant.EPOCH.plusSeconds(86400)))
                .isInstanceOf(com.elderlycare.platform.common.api.BusinessException.class);
        verifyNoInteractions(crypto, revisions);
    }
}

