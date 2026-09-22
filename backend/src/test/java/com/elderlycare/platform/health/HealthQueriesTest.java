package com.elderlycare.platform.health;

import com.elderlycare.platform.identity.domain.UserAccount;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 仅解析映射并验证权限SQL结构，不连接数据库、不替代MySQL检查。 */
class HealthQueriesTest {
    /** 列表与详情包含相同授权、绑定版本和接管限制，历史授权不复活。 */
    @Test void rendersSharedPermissionForListsAndDetails() throws Exception {
        var config = new Configuration();
        for (String path : List.of("mapper/health/HealthQueries.xml", "mapper/consent/HealthGrantQueries.xml")) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                new XMLMapperBuilder(in, config, path, config.getSqlFragments()).parse();
            }
        }
        var a = new UserAccount(); a.setId(1L); a.setCommunityId(2L); a.setRole("COMMUNITY_OPERATOR");
        var p = new HashMap<String, Object>(); p.put("actor", a); p.put("now", Instant.now()); p.put("elderId", 3L);
        p.put("id", 4L); p.put("limit", 20); p.put("offset", 0); p.put("ascending", false);
        for (String method : List.of("records", "recordCount", "record")) {
            String sql = config.getMappedStatement("com.elderlycare.platform.health.mapper.HealthQueries." + method).getBoundSql(p).getSql();
            assertThat(sql).contains("g.binding_version = eb.version", "eb.active = TRUE", "owner.status = 'ACTIVE'",
                    "recipient.status = 'ACTIVE'", "g.expires_at >", "g.revoked = FALSE",
                    "r.original_actor_id =", "r.owner_taken_over = FALSE", "e.status = 'ACTIVE'").doesNotContain("booking_grant");
        }
        a.setRole("FAMILY");
        assertThat(config.getMappedStatement("com.elderlycare.platform.health.mapper.HealthQueries.record").getBoundSql(p).getSql())
                .contains("r.status = 'ACTIVE'", "g.scope = 'FAMILY_READ'");
        a.setRole("PLATFORM_ADMIN");
        assertThat(config.getMappedStatement("com.elderlycare.platform.health.mapper.HealthQueries.record").getBoundSql(p).getSql()).contains("1 = 0");
        a.setRole("ELDER");
        assertThat(config.getMappedStatement("com.elderlycare.platform.health.mapper.HealthQueries.record").getBoundSql(p).getSql())
                .contains("eb.account_id =").doesNotContain("e.status = 'ACTIVE'");
        assertThat(config.getMappedStatement("com.elderlycare.platform.consent.mapper.HealthGrantQueries.visible").getBoundSql(p).getSql())
                .contains("eb.active = TRUE", "e.id = g.elder_id");
    }
}

