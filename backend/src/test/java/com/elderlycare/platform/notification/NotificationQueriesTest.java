package com.elderlycare.platform.notification;

import com.elderlycare.platform.identity.domain.UserAccount;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 仅验证权限SQL契约及映射渲染，不连接数据库，不代表MySQL兼容性或并发结论。 */
class NotificationQueriesTest {
    /** 投递、列表、计数和单条权限必须包含相同周期保护，转交通知强制隐藏业务编号。 */
    @Test void permissionEpochsAreSharedAcrossEveryNotificationReadPath() throws Exception {
        var config = configuration(); var actor = new UserAccount(); actor.setId(1L); actor.setCommunityId(9L); actor.setRole("FAMILY");
        var params = new HashMap<String, Object>(); params.put("actor", actor); params.put("now", Instant.now());
        params.put("unreadOnly", true); params.put("eventId", 7L); params.put("id", 8L); params.put("offset", 0L); params.put("limit", 20);
        for (String method : List.of("deliverable", "list", "count", "visible")) {
            String sql = sql(config, method, params);
            assertThat(sql).contains("a.notification_version = r.notification_version", "a.role = r.recipient_role",
                    "eb.version = r.binding_version", "g.notification_epoch = r.grant_epoch", "g.revoked = FALSE",
                    "g.expires_at >", "b.assignment_version = r.assignment_version", "b.worker_id = a.id", "a.status = 'ACTIVE'");
        }
        assertThat(sql(config, "list", params)).contains("THEN NULL ELSE o.booking_id END AS booking_id");
        assertThat(sql(config, "snapshots", params)).doesNotContain("g.can_book").contains("a.id !=");
    }

    /** 同一预约仅派生一种优先待办，家属不会得到管理待办，人员范围仍为当前安排。 */
    @Test void todosPreserveRoleScopeAndExceptionPriority() throws Exception {
        var config = configuration(); var actor = new UserAccount(); actor.setId(1L); actor.setCommunityId(9L);
        var params = new HashMap<String, Object>(); params.put("actor", actor); params.put("now", Instant.now());
        params.put("offset", 0L); params.put("limit", 20);
        actor.setRole("PLATFORM_ADMIN");
        String admin = sql(config, "todos", params);
        assertThat(admin.indexOf("THEN 'EXCEPTION'")).isLessThan(admin.indexOf("THEN 'OVERDUE'"));
        assertThat(admin).contains("'PENDING_CONFIRMATION'", "b.community_id =");
        actor.setRole("STAFF");
        assertThat(sql(config, "todos", params)).contains("b.worker_id =", "w.enabled = TRUE", "'WAITING_START'", "'IN_PROGRESS'");
        actor.setRole("FAMILY"); assertThat(sql(config, "todos", params)).contains("AND 1 = 0");
    }

    /** 装载预约共享对象范围以及通知查询，不创建数据源。 */
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        for (String path : List.of("mapper/booking/BookingQueries.xml", "mapper/notification/NotificationQueries.xml")) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                new XMLMapperBuilder(in, config, path, config.getSqlFragments()).parse();
            }
        }
        return config;
    }

    /** 获取绑定参数后的SQL文本，只用于验证不会遗漏权限条件。 */
    private String sql(Configuration config, String method, HashMap<String, Object> params) {
        return config.getMappedStatement("com.elderlycare.platform.notification.mapper.NotificationQueries." + method).getBoundSql(params).getSql();
    }
}
