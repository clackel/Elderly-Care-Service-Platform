package com.elderlycare.platform.notification;

import com.elderlycare.platform.booking.service.BookingAccess;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.common.api.PageQuery;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.notification.domain.MessageRow;
import com.elderlycare.platform.notification.mapper.NotificationQueries;
import com.elderlycare.platform.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证通知响应和已读入口的权限边界，已读不会完成业务待办。 */
class NotificationServiceTest {
    private final BookingAccess access = mock(BookingAccess.class);
    private final NotificationQueries queries = mock(NotificationQueries.class);
    private final NotificationService service = new NotificationService(access, queries);

    /** 越权消息在任何已读更新之前被拒绝；重复已读仍正常返回。 */
    @Test void markingReadRechecksVisibilityAndAllowsSafeRepeat() {
        var actor = new UserAccount(); when(access.writer(null)).thenReturn(actor);
        assertThatThrownBy(() -> service.read(null, 2L)).isInstanceOf(BusinessException.class);
        verify(queries, never()).markRead(any(), anyLong(), any());
        var row = new MessageRow(); row.readAt = Instant.now(); when(queries.visible(eq(actor), eq(2L), any())).thenReturn(row);
        assertThatCode(() -> service.read(null, 2L)).doesNotThrowAnyException();
        verify(queries).markRead(eq(actor), eq(2L), any());
    }

    /** 转交提示不暴露预约编号；列表和未读数均使用统一权限查询。 */
    @Test void transferredNoticeHasNoBusinessLinkAndUnreadUsesSameScope() {
        var actor = new UserAccount(); when(access.actor(null)).thenReturn(actor);
        var row = new MessageRow(); row.id = 2L; row.action = "TASK_TRANSFERRED"; row.occurredAt = Instant.now();
        when(queries.list(eq(actor), eq(true), any(), eq(0L), eq(20))).thenReturn(List.of(row));
        when(queries.count(eq(actor), eq(true), any())).thenReturn(1L);
        var page = service.list(null, new PageQuery(1, 20), true);
        assertThat(page.items().getFirst().bookingId()).isNull(); assertThat(page.total()).isEqualTo(service.unread(null).count());
    }
}
