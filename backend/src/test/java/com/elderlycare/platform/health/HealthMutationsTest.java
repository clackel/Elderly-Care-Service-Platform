package com.elderlycare.platform.health;

import com.elderlycare.platform.health.domain.HealthMutationRow;
import com.elderlycare.platform.health.mapper.HealthMutationMapper;
import com.elderlycare.platform.health.service.*;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.common.api.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class HealthMutationsTest {
    /** 同请求同摘要返回原版本，不同内容明确409。 */
    @Test void replaysOnlyMatchingHash() {
        var mapper = mock(HealthMutationMapper.class);
        var mutations = new HealthMutations(mapper, mock(HealthCrypto.class));
        var row = new HealthMutationRow(); row.requestHash = "same"; row.resultId = 3L; row.resultVersion = 2L;
        when(mapper.selectOne(any())).thenReturn(row);
        var a = new UserAccount(); a.setId(1L); a.setCommunityId(2L);
        assertThat(mutations.replay(a, "CORRECT", UUID.randomUUID(), "same").version()).isEqualTo(2L);
        assertThatThrownBy(() -> mutations.replay(a, "CORRECT", UUID.randomUUID(), "different")).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException)e).status().value()).isEqualTo(409));
        verify(mapper, never()).insert(any(HealthMutationRow.class));
    }
}

