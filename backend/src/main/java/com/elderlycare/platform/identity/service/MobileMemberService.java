package com.elderlycare.platform.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.booking.api.*;
import com.elderlycare.platform.booking.domain.*;
import com.elderlycare.platform.booking.mapper.*;
import com.elderlycare.platform.booking.service.*;
import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.UserAccountMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import static com.elderlycare.platform.booking.service.BookingAccess.*;

@Service
public class MobileMemberService {
    private final BookingAccess access;
    private final BookingCrypto crypto;
    private final UserAccountMapper accounts;
    private final EnrollmentMapper enrollments;
    private final WechatIdentityMapper identities;
    private final PasswordEncoder passwords;
    /** 装配社区核验开通组件，开通码不能授予管理员角色。 */
    public MobileMemberService(BookingAccess access, BookingCrypto crypto, UserAccountMapper accounts,
            EnrollmentMapper enrollments, WechatIdentityMapper identities, PasswordEncoder passwords) {
        this.access = access; this.crypto = crypto; this.accounts = accounts; this.enrollments = enrollments; this.identities = identities; this.passwords = passwords;
    }
    /** 社区分页读取已开通的老人、家属及工作人员账号摘要。 */
    public PageResponse<BookingResponses.Member> list(Authentication auth, PageQuery page) {
        var a = access.operator(auth);
        var q = new QueryWrapper<UserAccount>().eq("community_id", a.getCommunityId()).in("role", "ELDER", "FAMILY", "STAFF");
        long total = accounts.selectCount(q);
        var rows = accounts.selectList(q.orderByDesc("id").last("LIMIT " + page.pageSize() + " OFFSET " + page.offset()));
        return new PageResponse<>(rows.stream().map(this::view).toList(), page.page(), page.pageSize(), total);
    }
    /** 社区核验后幂等创建移动账号；随机密码不展示，不提供模拟登录。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponses.Member create(Authentication auth, BookingRequests.Member input) {
        var a = access.operator(auth); access.lock(a);
        String hash = crypto.hash("member", input);
        var row = enrollments.selectOne(new QueryWrapper<EnrollmentRow>().eq("community_id", a.getCommunityId()).eq("request_id", input.requestId().toString()));
        if (row != null) {
            if (!row.requestHash.equals(hash)) throw conflict("IDEMPOTENCY_CONFLICT", "请求编号已用于其他账号开通内容");
            return view(accounts.selectById(row.id));
        }
        var member = new UserAccount(); member.setId(IdWorker.getId()); member.setCommunityId(a.getCommunityId());
        member.setUsername("mobile." + member.getId()); member.setPasswordHash(passwords.encode(crypto.token()));
        member.setDisplayName(input.displayName().strip()); member.setRole(input.role()); member.setStatus("ACTIVE"); accounts.insert(member);
        row = new EnrollmentRow(); row.id = member.getId(); row.communityId = a.getCommunityId();
        row.requestId = input.requestId().toString(); row.requestHash = hash; row.used = false; row.expiresAt = Instant.now();
        row.evidenceCipher = crypto.encrypt("enrollment", row.communityId, row.id, input.verificationReference().strip()); enrollments.insert(row);
        access.audit(a, row.id, "MOBILE_MEMBER_VERIFY"); return view(member);
    }
    /** 签发30分钟单次开通码；重新签发会立即废止旧码，原值仅本次返回。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponses.Enrollment issue(Authentication auth, long id) {
        var a = access.operator(auth); access.lock(a);
        var row = enrollments.selectById(id);
        if (row == null || !Objects.equals(row.communityId, a.getCommunityId())) throw missing();
        if (identities.selectById(id) != null) throw conflict("WECHAT_ALREADY_LINKED", "账号已经绑定微信，无需再次开通");
        String token = crypto.token(); row.tokenHash = crypto.hash("enrollment-token", token);
        row.used = false; row.expiresAt = Instant.now().plusSeconds(1800); enrollments.updateById(row);
        access.audit(a, id, "MOBILE_ENROLLMENT_ISSUE");
        return new BookingResponses.Enrollment(Long.toString(id), token, row.expiresAt);
    }
    /** 生成不含登录名、密码或核验正文的移动账号摘要。 */
    private BookingResponses.Member view(UserAccount a) {
        return new BookingResponses.Member(a.getId().toString(), a.getDisplayName(), a.getRole(), a.getStatus(), identities.selectById(a.getId()) != null);
    }
}
