package com.elderlycare.platform.identity;

import com.elderlycare.platform.booking.service.BookingCrypto;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.identity.api.AdminAccountRequests;
import com.elderlycare.platform.identity.domain.*;
import com.elderlycare.platform.identity.mapper.*;
import com.elderlycare.platform.identity.service.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 用合成账号核验后台生命周期的权限、撤销及重复写规则，不连接数据库。 */
class AdminAccountServiceTest {
    private final CommunityAccess access = mock(CommunityAccess.class);
    private final CommunityMapper communities = mock(CommunityMapper.class);
    private final UserAccountMapper accounts = mock(UserAccountMapper.class);
    private final AdminAccountQueries queries = mock(AdminAccountQueries.class);
    private final AdminAccountEventMapper events = mock(AdminAccountEventMapper.class);
    private final AdminAccountMutationMapper mutations = mock(AdminAccountMutationMapper.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final BookingCrypto crypto = mock(BookingCrypto.class);
    private final AdminAccountService service = new AdminAccountService(access, communities, accounts, queries, events, mutations, passwords, crypto);
    private UserAccount admin;
    private Authentication authentication;

    /** 准备合成社区管理员及成功持久化返回值，不模拟绕过会话版本的身份。 */
    @BeforeEach void prepare() {
        admin = account(1L, "PLATFORM_ADMIN"); authentication = AccountSessions.authentication(admin);
        when(access.current(authentication)).thenReturn(admin);
        when(communities.findActiveById(9L)).thenReturn(new Community(9L, "合成社区"));
        when(communities.lockActiveById(9L)).thenReturn(new Community(9L, "合成社区"));
        when(crypto.hash(anyString(), any())).thenReturn("synthetic-hmac");
        when(events.insert(any(AdminAccountEventRow.class))).thenReturn(1);
        when(mutations.insert(any(AdminAccountMutationRow.class))).thenReturn(1);
        when(queries.update(any(), anyLong(), any())).thenReturn(1);
    }

    /** 停用运营账号撤销全部代录授权，并提高凭证版本；恢复状态不重建已撤销授权。 */
    @Test void disablingOperatorRevokesAssistancePermanentlyAndInvalidatesSessions() {
        var target = account(2L, "COMMUNITY_OPERATOR"); var oldSession = AccountSessions.authentication(target);
        when(queries.target(9L, 2L)).thenReturn(target);
        when(queries.revokeAssistance(eq(9L), eq(2L), eq(1L), any())).thenReturn(2);
        var disabled = service.update(authentication, 2, update(0, "COMMUNITY_OPERATOR", "DISABLED"));
        assertThat(disabled.version()).isEqualTo(1); assertThat(target.getCredentialVersion()).isEqualTo(1);
        verify(queries).revokeAssistance(eq(9L), eq(2L), eq(1L), any());
        service.update(authentication, 2, update(1, "COMMUNITY_OPERATOR", "ACTIVE"));
        assertThat(target.getCredentialVersion()).isEqualTo(2);
        assertThat(target.getNotificationVersion()).isEqualTo(2);
        verify(queries, times(1)).revokeAssistance(anyLong(), anyLong(), anyLong(), any());
        assertThatThrownBy(() -> AccountSessions.requireCurrent(oldSession, target)).isInstanceOf(BusinessException.class);
    }

    /** 离开运营角色同样撤销代录授权，普通名称修改不触发凭证版本变化。 */
    @Test void changingRoleRevokesAssistanceWhileRenamePreservesSessionVersion() {
        var target = account(2L, "COMMUNITY_OPERATOR"); when(queries.target(9L, 2L)).thenReturn(target);
        service.update(authentication, 2, new AdminAccountRequests.Update(UUID.randomUUID(), 0L, "新的合成名称", "COMMUNITY_OPERATOR", "ACTIVE"));
        assertThat(target.getCredentialVersion()).isZero();
        assertThat(target.getNotificationVersion()).isZero();
        verify(queries, never()).revokeAssistance(anyLong(), anyLong(), anyLong(), any());
        service.update(authentication, 2, update(1, "AUDITOR", "ACTIVE"));
        assertThat(target.getCredentialVersion()).isEqualTo(1);
        assertThat(target.getNotificationVersion()).isEqualTo(1);
        verify(queries).revokeAssistance(eq(9L), eq(2L), eq(1L), any());
    }

    /** 禁止自停用、自降权和自助管理员重置，防止绕过原密码检查。 */
    @Test void selfAdministrativeLockoutAndPasswordResetAreRejected() {
        when(queries.target(9L, 1L)).thenReturn(admin);
        assertThatThrownBy(() -> service.update(authentication, 1, update(0, "PLATFORM_ADMIN", "DISABLED")))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("SELF_ACCOUNT_CHANGE_FORBIDDEN");
        assertThatThrownBy(() -> service.update(authentication, 1, update(0, "AUDITOR", "ACTIVE")))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("SELF_ACCOUNT_CHANGE_FORBIDDEN");
        assertThatThrownBy(() -> service.resetPassword(authentication, 1,
                new AdminAccountRequests.ResetPassword(UUID.randomUUID(), 0L, "SyntheticPass2026")))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("SELF_PASSWORD_RESET_FORBIDDEN");
        verify(queries, never()).update(any(), anyLong(), any()); verifyNoInteractions(passwords, events);
    }

    /** 保护最后有效管理员，并在旧版本请求时保持账号状态不变。 */
    @Test void lastAdministratorAndStaleVersionCannotBeOverwritten() {
        var target = account(2L, "PLATFORM_ADMIN"); when(queries.target(9L, 2L)).thenReturn(target);
        when(queries.activeAdmins(9L)).thenReturn(1L);
        assertThatThrownBy(() -> service.update(authentication, 2, update(0, "AUDITOR", "ACTIVE")))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("LAST_ADMIN_REQUIRED");
        target.setVersion(2);
        assertThatThrownBy(() -> service.update(authentication, 2, update(0, "PLATFORM_ADMIN", "ACTIVE")))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("VERSION_CONFLICT");
        verify(queries, never()).update(any(), anyLong(), any());
    }

    /** 取得社区锁后必须重读账号凭证版本，等待期间被重置密码的请求不能写入。 */
    @Test void credentialChangeWhileWaitingForLockPreventsMutation() {
        var changed = account(1L, "PLATFORM_ADMIN"); changed.setCredentialVersion(1);
        when(access.current(authentication)).thenReturn(admin, changed);
        assertThatThrownBy(() -> service.create(authentication, create()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("SESSION_REVOKED");
        verifyNoInteractions(accounts, queries, events, mutations, passwords);
    }

    /** 创建账号固定社区并归一登录名，重复请求返回原结果且不同内容不能复用编号。 */
    @Test void createAndRetryDoNotDuplicateAccountsOrAcceptChangedPayload() {
        when(accounts.insert(any(UserAccount.class))).thenReturn(1); when(passwords.encode(anyString())).thenReturn("synthetic-hash");
        var input = create(); var result = service.create(authentication, input);
        verify(accounts).insert(argThat((UserAccount row) -> row.getUsername().equals("test.admin")
                && row.getCommunityId() == 9L && row.isPasswordChangeRequired()));
        var stored = new AdminAccountMutationRow(); stored.requestHash = "synthetic-hmac";
        stored.resultId = Long.parseLong(result.id()); stored.resultVersion = 0L;
        when(mutations.selectOne(any())).thenReturn(stored);
        assertThat(service.create(authentication, input)).isEqualTo(result);
        verify(accounts, times(1)).insert(any(UserAccount.class));
        when(crypto.hash(anyString(), any())).thenReturn("different-hmac");
        assertThatThrownBy(() -> service.create(authentication, input)).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    /** 改密必须核验旧密码并拒绝复用，成功后退出初始改密状态并撤销旧凭据。 */
    @Test void passwordChangeRequiresOldPasswordAndRevokesOldSession() {
        admin.setPasswordChangeRequired(true);
        var input = new AdminAccountRequests.ChangePassword(UUID.randomUUID(), "OldSynthetic2026", "NewSynthetic2026");
        assertThatThrownBy(() -> service.changePassword(authentication,
                new AdminAccountRequests.ChangePassword(UUID.randomUUID(), "密".repeat(25), "NewSynthetic2026")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(passwords);
        assertThatThrownBy(() -> service.changePassword(authentication, input)).isInstanceOf(BusinessException.class);
        when(passwords.matches("OldSynthetic2026", "synthetic-hash")).thenReturn(true);
        when(passwords.matches("NewSynthetic2026", "synthetic-hash")).thenReturn(true);
        assertThatThrownBy(() -> service.changePassword(authentication, input)).isInstanceOf(BusinessException.class);
        verify(queries, never()).update(any(), anyLong(), any());
        when(passwords.matches("NewSynthetic2026", "synthetic-hash")).thenReturn(false);
        when(passwords.encode("NewSynthetic2026")).thenReturn("new-synthetic-hash");
        service.changePassword(authentication, input);
        assertThat(admin.isPasswordChangeRequired()).isFalse(); assertThat(admin.getCredentialVersion()).isEqualTo(1);
        assertThat(admin.getNotificationVersion()).isZero();
        assertThatThrownBy(() -> AccountSessions.requireCurrent(authentication, admin)).isInstanceOf(BusinessException.class);
    }

    /** 重置密码立即提高凭证版本并要求下次改密，原版本请求重试不再改写。 */
    @Test void resettingAnotherAccountForcesPasswordChangeAndReplaysBeforeVersionCheck() {
        var target = account(2L, "DUTY_OFFICER"); when(queries.target(9L, 2L)).thenReturn(target);
        when(passwords.encode("NewSynthetic2026")).thenReturn("new-synthetic-hash");
        var input = new AdminAccountRequests.ResetPassword(UUID.randomUUID(), 0L, "NewSynthetic2026");
        var result = service.resetPassword(authentication, 2, input);
        assertThat(target.isPasswordChangeRequired()).isTrue(); assertThat(target.getCredentialVersion()).isEqualTo(1);
        assertThat(target.getNotificationVersion()).isZero();
        var stored = new AdminAccountMutationRow(); stored.requestHash = "synthetic-hmac"; stored.resultId = 2L; stored.resultVersion = 1L;
        when(mutations.selectOne(any())).thenReturn(stored);
        assertThat(service.resetPassword(authentication, 2, input)).isEqualTo(result);
        verify(queries, times(1)).update(any(), anyLong(), any());
    }

    /** 移动账号或跨社区对象无可见结果时，禁止利用重试请求越过对象边界。 */
    @Test void inaccessibleAccountIsRejectedBeforeReadingIdempotentResult() {
        assertThatThrownBy(() -> service.update(authentication, 2, update(0, "AUDITOR", "ACTIVE")))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("ACCOUNT_NOT_FOUND");
        verifyNoInteractions(mutations, passwords, events);
    }

    /** 创建具备默认版本的合成后台账号。 */
    private static UserAccount account(long id, String role) {
        var account = new UserAccount(); account.setId(id); account.setCommunityId(9L); account.setUsername("test." + id);
        account.setDisplayName("合成账号"); account.setRole(role); account.setStatus("ACTIVE"); account.setPasswordHash("synthetic-hash");
        return account;
    }

    /** 使用固定合成字段创建不同请求编号的账号修改请求。 */
    private static AdminAccountRequests.Update update(long version, String role, String status) {
        return new AdminAccountRequests.Update(UUID.randomUUID(), version, "合成账号", role, status);
    }

    /** 构造只使用合成名称和密码的账号创建请求。 */
    private static AdminAccountRequests.Create create() {
        return new AdminAccountRequests.Create(UUID.randomUUID(), " Test.Admin ", "合成后台账号", "COMMUNITY_OPERATOR", "SyntheticPass2026");
    }
}
