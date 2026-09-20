package com.elderlycare.platform.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderlycare.platform.booking.domain.*;
import com.elderlycare.platform.booking.mapper.*;
import com.elderlycare.platform.booking.service.BookingCrypto;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.dao.DuplicateKeyException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.elderlycare.platform.booking.service.BookingAccess.*;

@Service
public class WechatLoginService {
    private final String appId;
    private final String secret;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final BookingCrypto crypto;
    private final WechatIdentityMapper identities;
    private final EnrollmentMapper enrollments;
    private final UserAccountMapper accounts;
    private final CommunityMapper communities;
    private final TransactionTemplate transactions;
    private final Map<String, Attempt> attempts = new HashMap<>();
    private record Attempt(Instant until, int count) {}
    /** 外部凭证由环境配置；身份交换不接收客户端自报的openid或账号编号。 */
    public WechatLoginService(@Value("${app.integrations.wechat-app-id:}") String appId,
            @Value("${app.integrations.wechat-app-secret:}") String secret, ObjectMapper json, BookingCrypto crypto,
            WechatIdentityMapper identities, EnrollmentMapper enrollments, UserAccountMapper accounts,
            CommunityMapper communities, PlatformTransactionManager manager) {
        this.appId = appId; this.secret = secret; this.json = json; this.crypto = crypto;
        this.identities = identities; this.enrollments = enrollments; this.accounts = accounts; this.communities = communities;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    /** 缺少真实小程序凭证时不宣称登录可用。 */
    public boolean enabled() { return !appId.isBlank() && !secret.isBlank(); }
    /** 限制单实例来源请求频率并回收过期计数，不信任客户端转发地址头。 */
    private synchronized void throttle(String address) {
        var now = Instant.now(); attempts.entrySet().removeIf(e -> e.getValue().until().isBefore(now));
        String key = crypto.hash("login-peer", address);
        var attempt = attempts.get(key);
        if ((attempt != null && attempt.count() >= 20) || (attempt == null && attempts.size() >= 10000))
            throw error(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", "登录请求过于频繁，请一分钟后重试");
        attempts.put(key, new Attempt(attempt == null ? now.plusSeconds(60) : attempt.until(), attempt == null ? 1 : attempt.count() + 1));
    }
    /** 向微信校验临时凭证后加载已核验账号，首次绑定消耗一次性开通码。 */
    public UserAccount login(String code, String token, String address) {
        if (!enabled()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_NOT_CONFIGURED", "微信登录尚未配置，请联系社区");
        throttle(address);
        String subject = exchange(code);
        try {
            return transactions.execute(status -> bind(subject, token));
        } catch (DuplicateKeyException e) {
            throw conflict("WECHAT_BINDING_CONFLICT", "微信或账号已完成其他绑定，请重新登录");
        }
    }
    /** 固定请求微信官方身份接口；不记录含AppSecret的URL或底层异常。 */
    private String exchange(String code) {
        try {
            String query = "appid=" + encode(appId) + "&secret=" + encode(secret) + "&js_code=" + encode(code) + "&grant_type=authorization_code";
            var req = HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com/sns/jscode2session?" + query)).timeout(Duration.ofSeconds(8)).GET().build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) throw error(HttpStatus.BAD_GATEWAY, "WECHAT_UNAVAILABLE", "微信身份服务暂不可用，请稍后重试");
            var body = json.readTree(res.body());
            if (body.path("errcode").asInt(0) != 0 || body.path("openid").asText().isBlank())
                throw error(HttpStatus.UNAUTHORIZED, "WECHAT_CODE_INVALID", "微信登录凭证已失效，请重新登录");
            return crypto.hash("wechat-subject", appId + ":" + body.path("openid").asText());
        } catch (com.elderlycare.platform.common.api.BusinessException e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw error(HttpStatus.BAD_GATEWAY, "WECHAT_UNAVAILABLE", "微信登录暂时中断，请稍后重试");
        } catch (Exception e) { throw error(HttpStatus.BAD_GATEWAY, "WECHAT_UNAVAILABLE", "无法完成微信身份验证，请稍后重试"); }
    }
    /** 在社区锁和唯一约束下消费核验开通码，防止并发绑定或账号替换。 */
    private UserAccount bind(String subject, String token) {
        var identity = identities.selectOne(new QueryWrapper<WechatIdentityRow>().eq("subject_hash", subject));
        if (identity != null) return active(identity.id);
        if (token == null || token.isBlank()) throw error(HttpStatus.FORBIDDEN, "ENROLLMENT_REQUIRED", "请联系社区核验身份并获取开通码");
        var enrollment = enrollments.selectOne(new QueryWrapper<EnrollmentRow>().eq("token_hash", crypto.hash("enrollment-token", token)));
        if (enrollment == null) throw error(HttpStatus.FORBIDDEN, "ENROLLMENT_INVALID", "开通码无效或已过期，请联系社区重新获取");
        if (communities.lockActiveById(enrollment.communityId) == null) throw error(HttpStatus.FORBIDDEN, "COMMUNITY_UNAVAILABLE", "所属社区不可用");
        enrollment = enrollments.selectById(enrollment.id);
        if (enrollment.used || !enrollment.expiresAt.isAfter(Instant.now()) || !Objects.equals(enrollment.tokenHash, crypto.hash("enrollment-token", token)))
            throw error(HttpStatus.FORBIDDEN, "ENROLLMENT_INVALID", "开通码无效或已过期，请联系社区重新获取");
        var account = active(enrollment.id);
        if (identities.selectById(account.getId()) != null) throw conflict("WECHAT_ALREADY_LINKED", "账号已绑定微信，请使用原微信登录");
        identity = new WechatIdentityRow(); identity.id = account.getId(); identity.subjectHash = subject; identities.insert(identity);
        enrollment.used = true; enrollment.tokenHash = null; enrollments.updateById(enrollment);
        return account;
    }
    /** 只允许有效社区的真实移动角色账号登录，账号停用立即拒绝。 */
    private UserAccount active(long id) {
        var a = accounts.selectById(id);
        if (a == null || !"ACTIVE".equals(a.getStatus()) || !Set.of("ELDER","FAMILY","STAFF").contains(a.getRole())
                || a.getCommunityId() == null || communities.findActiveById(a.getCommunityId()) == null)
            throw error(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "账号或所属社区不可用，请联系社区处理");
        return a;
    }
    /** 编码微信身份接口的固定参数值。 */
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
