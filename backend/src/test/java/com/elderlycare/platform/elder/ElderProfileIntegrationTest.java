package com.elderlycare.platform.elder;

import com.elderlycare.platform.elder.api.ElderRequests;
import com.elderlycare.platform.elder.domain.ElderProfileData;
import com.elderlycare.platform.elder.mapper.ElderAuditMapper;
import com.elderlycare.platform.elder.service.ElderProfileService;
import com.elderlycare.platform.common.api.BusinessException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.net.*;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${ELDER_TEST_DB_URL:jdbc:h2:mem:elders;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${ELDER_TEST_DB_USERNAME:sa}",
        "spring.datasource.password=${ELDER_TEST_DB_PASSWORD:}",
        "spring.datasource.driver-class-name=${ELDER_TEST_DB_DRIVER:org.h2.Driver}",
        "app.dev-password=Integration-Only-2026"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class ElderProfileIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ElderProfileService service;
    @MockitoSpyBean ElderAuditMapper audits;
    @LocalServerPort int port;

    /** 在专用测试数据库中重置合成数据，保留迁移及默认开发账号。 */
    @BeforeEach
    void resetData() {
        reset(audits);
        jdbc.update("DELETE FROM elder_profile_event");
        jdbc.update("DELETE FROM elder_profile");
        jdbc.update("DELETE FROM user_account WHERE id=10002");
        jdbc.update("DELETE FROM community WHERE id=10002");
        jdbc.update("UPDATE community SET status='ACTIVE' WHERE id=10001");
        jdbc.update("UPDATE user_account SET role='COMMUNITY_OPERATOR',status='ACTIVE',community_id=10001 WHERE id=10001");
        jdbc.update("INSERT INTO community(id,name) VALUES(10002,'隔离测试社区')");
        jdbc.update("""
            INSERT INTO user_account(id,username,password_hash,display_name,role,community_id)
            VALUES(10002,'other.operator',?,'其他社区运营','COMMUNITY_OPERATOR',10002)
            """, passwords.encode("Integration-Only-2026"));
    }

    @Test
    @DisplayName("建档、修改、归档、恢复和修改历史形成完整闭环")
    void lifecycleAndHistory() throws Exception {
        var session = login("dev.operator");
        var data = profile("张测试", "13800138000");
        var created = create(session, data, UUID.randomUUID());
        String id = created.get("id").asText();
        assertThat(created.get("version").asLong()).isZero();
        mvc.perform(get("/api/v1/elders/" + id).session(session)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.profile.address").value("测试路1号"))
                .andExpect(jsonPath("$.data.profile.birthDateInRange").doesNotExist());
        data.put("address", "测试路2号");
        mvc.perform(put("/api/v1/elders/" + id).session(session).with(csrf())
                .contentType("application/json").content(updateBody(0, data))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(put("/api/v1/elders/" + id).session(session).with(csrf())
                .contentType("application/json").content(updateBody(0, data))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ELDER_VERSION_CONFLICT"));
        mvc.perform(post("/api/v1/elders/" + id + "/archive").session(session).with(csrf())
                .contentType("application/json").content("{\"version\":1}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mvc.perform(put("/api/v1/elders/" + id).session(session).with(csrf())
                .contentType("application/json").content(updateBody(2, data))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ELDER_ARCHIVED"));
        mvc.perform(post("/api/v1/elders/" + id + "/archive").session(session).with(csrf())
                .contentType("application/json").content("{\"version\":2}")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/elders/" + id + "/restore").session(session).with(csrf())
                .contentType("application/json").content("{\"version\":2}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(3));
        mvc.perform(get("/api/v1/elders/" + id + "/history").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.items[0].action").value("RESTORE"))
                .andExpect(jsonPath("$.data.items[2].changedFields[0]").value("address"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder_profile_event WHERE action='VIEW'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("重复建档幂等、重复身份约束和共用电话规则")
    void creationIsIdempotentAndPhoneIsNotIdentity() throws Exception {
        var session = login("dev.operator");
        UUID request = UUID.randomUUID();
        var data = profile("李测试", "13800138000");
        var created = create(session, data, request);
        assertThat(create(session, data, request).get("id")).isEqualTo(created.get("id"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder_profile_event", Integer.class)).isEqualTo(1);
        var different = data.deepCopy().put("address", "不同地址");
        mvc.perform(post("/api/v1/elders").session(session).with(csrf()).contentType("application/json")
                .content(createBody(request, different))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        mvc.perform(post("/api/v1/elders").session(session).with(csrf()).contentType("application/json")
                .content(createBody(UUID.randomUUID(), data))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ELDER_ALREADY_EXISTS"));
        create(session, profile("王测试", "13800138000"), UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder_profile", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("列表精确检索、状态过滤、分页和脱敏")
    void listSearchAndPagination() throws Exception {
        var session = login("dev.operator");
        String id = create(session, profile("张测试", "13800138000"), UUID.randomUUID()).get("id").asText();
        create(session, profile("李测试", "021-12345678"), UUID.randomUUID());
        mvc.perform(get("/api/v1/elders").session(session).param("keyword", "张测试")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].maskedPhone").value("138****8000"))
                .andExpect(jsonPath("$.data.items[0].address").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].emergencyContactPhone").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].payloadCipher").doesNotExist());
        mvc.perform(get("/api/v1/elders").session(session).param("keyword", "02112345678"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/elders").session(session).param("keyword", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/elders").session(session).param("page", "2").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(post("/api/v1/elders/" + id + "/archive").session(session).with(csrf())
                .contentType("application/json").content("{\"version\":0}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/elders").session(session).param("status", "ARCHIVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/elders").session(session).param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/elders").session(session).param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("角色和社区范围约束覆盖查询、修改和状态操作")
    void scopeAndRolesCannotBeBypassed() throws Exception {
        var operator = login("dev.operator");
        var other = login("other.operator");
        var data = profile("张测试", "13800138000");
        String id = create(operator, data, UUID.randomUUID()).get("id").asText();
        mvc.perform(get("/api/v1/elders").session(other).param("communityId","10001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        for (String suffix : List.of("", "/history")) {
            mvc.perform(get("/api/v1/elders/" + id + suffix).session(other)).andExpect(status().isNotFound());
        }
        mvc.perform(put("/api/v1/elders/" + id).session(other).with(csrf()).contentType("application/json")
                .content(updateBody(0,data))).andExpect(status().isNotFound());
        for (String action : List.of("archive","restore")) {
            mvc.perform(post("/api/v1/elders/" + id + "/" + action).session(other).with(csrf())
                    .contentType("application/json").content("{\"version\":0}")).andExpect(status().isNotFound());
        }
        for (String role : List.of("ELDER","FAMILY","STAFF","DUTY_OFFICER","AUDITOR")) {
            jdbc.update("UPDATE user_account SET role=? WHERE id=10001",role);
            mvc.perform(get("/api/v1/elders").session(operator)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/elders").session(operator).with(csrf()).contentType("application/json")
                    .content(createBody(UUID.randomUUID(),data))).andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE user_account SET role='PLATFORM_ADMIN',community_id=NULL WHERE id=10001");
        mvc.perform(get("/api/v1/elders").session(operator)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMUNITY_REQUIRED"));
        jdbc.update("UPDATE user_account SET community_id=10001 WHERE id=10001");
        jdbc.update("UPDATE community SET status='DISABLED' WHERE id=10001");
        mvc.perform(get("/api/v1/elders").session(operator)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMUNITY_UNAVAILABLE"));
        jdbc.update("UPDATE user_account SET status='DISABLED' WHERE id=10001");
        mvc.perform(get("/api/v1/elders").session(operator)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("参数校验、CSRF及匿名访问")
    void validationAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/elders")).andExpect(status().isUnauthorized());
        var session = login("dev.operator");
        var valid = profile("张测试", "13800138000");
        mvc.perform(post("/api/v1/elders").session(session).contentType("application/json")
                .content(createBody(UUID.randomUUID(), valid))).andExpect(status().isForbidden());
        List<ObjectNode> invalid = List.of(valid.deepCopy().put("name"," "),
                valid.deepCopy().put("name","长".repeat(51)),
                valid.deepCopy().put("birthDate", LocalDate.now().plusDays(1).toString()),
                valid.deepCopy().put("birthDate","1899-01-01"),
                valid.deepCopy().put("phone","12345"),
                valid.deepCopy().put("emergencyContactPhone",""),
                valid.deepCopy().put("address",""),
                valid.deepCopy().put("gender","INVALID"));
        for (var data : invalid) {
            mvc.perform(post("/api/v1/elders").session(session).with(csrf()).contentType("application/json")
                    .content(createBody(UUID.randomUUID(), data))).andExpect(status().isBadRequest());
        }
        for (var query : Map.of("page","0","pageSize","101","status","INVALID","keyword","长".repeat(51)).entrySet()) {
            mvc.perform(get("/api/v1/elders").session(session).param(query.getKey(),query.getValue()))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/elders").session(session).with(csrf()).contentType("application/json")
                .content("{\"requestId\":\"bad\",\"profile\":null}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder_profile", Integer.class)).isZero();
        valid.putNull("phone");
        String id = create(session,valid,UUID.randomUUID()).get("id").asText();
        mvc.perform(put("/api/v1/elders/" + id).session(session).with(csrf()).contentType("application/json")
                .content("{\"profile\":" + valid + "}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/elders/" + id + "/archive").session(session).with(csrf())
                .contentType("application/json").content("{\"version\":-1}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/elders/-1").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/elders/9223372036854775808").session(session)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("个人信息加密保存且密文不能跨档案替换")
    void encryptionAndTamperResistance() throws Exception {
        var session = login("dev.operator");
        String first = create(session, profile("张测试","13800138000"), UUID.randomUUID()).get("id").asText();
        String second = create(session, profile("李测试","13900139000"), UUID.randomUUID()).get("id").asText();
        String cipher = jdbc.queryForObject("SELECT payload_cipher FROM elder_profile WHERE id=?",String.class,Long.valueOf(first));
        assertThat(cipher).startsWith("v1:").doesNotContain("张测试","13800138000","测试路","紧急联系人");
        jdbc.update("UPDATE elder_profile SET payload_cipher=? WHERE id=?",cipher,Long.valueOf(second));
        mvc.perform(get("/api/v1/elders/" + second).session(session)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder_profile_event WHERE action='VIEW'",Integer.class)).isZero();
    }

    @Test
    @DisplayName("审计失败时建档事务回滚")
    void auditFailureRollsBackWrite() throws Exception {
        doThrow(new IllegalStateException("审计存储不可用")).when(audits).insert(any());
        mvc.perform(post("/api/v1/elders").session(login("dev.operator")).with(csrf()).contentType("application/json")
                .content(createBody(UUID.randomUUID(),profile("张测试","13800138000")))).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder_profile", Integer.class)).isZero();
    }

    @Test
    @DisplayName("更新为重复身份时保留原资料和版本")
    void duplicateUpdateRollsBack() throws Exception {
        var session = login("dev.operator");
        var firstData = profile("张测试","13800138000");
        create(session, firstData, UUID.randomUUID());
        String second = create(session,profile("李测试","13900139000"),UUID.randomUUID()).get("id").asText();
        mvc.perform(put("/api/v1/elders/" + second).session(session).with(csrf()).contentType("application/json")
                .content(updateBody(0,firstData))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ELDER_ALREADY_EXISTS"));
        mvc.perform(get("/api/v1/elders/" + second).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.profile.name").value("李测试"));
    }

    @Test
    @DisplayName("并发重复建档只保留一条档案及创建事件")
    void concurrentCreate() throws Exception {
        var input = new ElderRequests.Create(UUID.randomUUID(),json.treeToValue(profile("张测试","13800138000"),ElderProfileData.class));
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> task = () -> {
                if (!gate.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("等待并发测试启动超时");
                return service.create(UsernamePasswordAuthenticationToken.authenticated("dev.operator",null,List.of()),input).id();
            };
            var first = executor.submit(task); var second = executor.submit(task); gate.countDown();
            assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(second.get(15,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder_profile",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder_profile_event",Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("并发修改同一版本时仅允许一个写入者成功")
    void concurrentUpdate() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("dev.operator",null,List.of());
        var original = json.treeToValue(profile("张测试","13800138000"),ElderProfileData.class);
        long id = Long.parseLong(service.create(auth,new ElderRequests.Create(UUID.randomUUID(),original)).id());
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<String>> results = new ArrayList<>();
            for (String address : List.of("地址甲","地址乙")) {
                results.add(executor.submit(() -> {
                    if (!gate.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("等待并发测试启动超时");
                    var data = json.treeToValue(profile("张测试","13800138000").put("address",address),ElderProfileData.class);
                    try { service.update(auth,id,new ElderRequests.Update(0L,data)); return "OK"; }
                    catch (BusinessException e) { return e.code(); }
                }));
            }
            gate.countDown();
            assertThat(List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK","ELDER_VERSION_CONFLICT");
        }
        assertThat(jdbc.queryForObject("SELECT version FROM elder_profile WHERE id=?",Long.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder_profile_event WHERE action='UPDATE'",Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("真实HTTP会话及CSRF可完成建档和详情查询")
    void realHttpRoundTrip() throws Exception {
        var cookies = new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        try (var client = HttpClient.newBuilder().cookieHandler(cookies).build()) {
            String base = "http://127.0.0.1:" + port + "/api/v1";
            var csrfResponse = client.send(HttpRequest.newBuilder(URI.create(base + "/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
            var token = json.readTree(csrfResponse.body()).get("data");
            var login = client.send(HttpRequest.newBuilder(URI.create(base + "/auth/admin/login"))
                    .header(token.get("headerName").asText(),token.get("token").asText())
                    .header("Content-Type","application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=dev.operator&password=Integration-Only-2026")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).isEqualTo(200);
            token = json.readTree(client.send(HttpRequest.newBuilder(URI.create(base + "/auth/csrf")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body()).get("data");
            var created = client.send(HttpRequest.newBuilder(URI.create(base + "/elders"))
                    .header(token.get("headerName").asText(),token.get("token").asText())
                    .header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(createBody(UUID.randomUUID(),profile("网络测试","13800138000")))).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(created.statusCode()).isEqualTo(200);
            String id = json.readTree(created.body()).get("data").get("id").asText();
            var detail = client.send(HttpRequest.newBuilder(URI.create(base + "/elders/" + id)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(detail.statusCode()).isEqualTo(200);
            assertThat(json.readTree(detail.body()).get("data").get("profile").get("name").asText()).isEqualTo("网络测试");
        }
    }

    /** 通过真实安全过滤器建立开发账号会话。 */
    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/admin/login").with(csrf())
                .param("username",username).param("password","Integration-Only-2026"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    /** 返回用于测试的完整合成资料。 */
    private ObjectNode profile(String name, String phone) {
        return json.createObjectNode().put("name",name).put("gender","FEMALE").put("birthDate","1950-01-01")
                .put("phone",phone).put("address","测试路1号").put("livingArrangement","WITH_FAMILY")
                .put("emergencyContactName","紧急联系人").put("emergencyContactPhone","13900139000")
                .put("emergencyContactRelation","子女").put("remark","测试资料");
    }

    /** 生成带幂等编号的建档请求体。 */
    private String createBody(UUID request, ObjectNode profile) throws Exception {
        return json.writeValueAsString(json.createObjectNode().put("requestId",request.toString()).set("profile",profile));
    }

    /** 生成携带乐观锁版本的完整更新请求体。 */
    private String updateBody(long version, ObjectNode profile) throws Exception {
        return json.writeValueAsString(json.createObjectNode().put("version",version).set("profile",profile));
    }

    /** 提交建档请求并提取成功返回的档案详情。 */
    private JsonNode create(MockHttpSession session, ObjectNode profile, UUID request) throws Exception {
        var result = mvc.perform(post("/api/v1/elders").session(session).with(csrf()).contentType("application/json")
                .content(createBody(request,profile))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
