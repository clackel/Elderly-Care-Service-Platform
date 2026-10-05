package com.elderlycare.platform.identity.service;

import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.UserAccountMapper;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class IdentityService implements UserDetailsService {
    private final UserAccountMapper mapper;
    private final CommunityMapper communities;

    /** 装配账号与有效社区查询，密码入口只允许后台角色。 */
    public IdentityService(UserAccountMapper mapper, CommunityMapper communities) { this.mapper = mapper; this.communities = communities; }

    /** 按已认证或规范化的用户名读取账号，不输出密码等内部字段。 */
    public UserAccount find(String username) {
        return mapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
    }

    /** 加载后台密码校验快照，统一隐藏账号不存在、停用和移动角色的差异。 */
    @Override
    public UserDetails loadUserByUsername(String username) {
        UserAccount account = find(username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
        if (account == null || !"ACTIVE".equals(account.getStatus()) || !AccountSessions.ADMIN_ROLES.contains(account.getRole())
                || account.getCommunityId() == null || communities.findActiveById(account.getCommunityId()) == null) {
            throw new UsernameNotFoundException("账号或密码错误");
        }
        return new AccountSessions.LoginUser(account);
    }
}
