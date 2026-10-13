package com.hrplatform.admin;

import com.hrplatform.auth.SysUser;
import com.hrplatform.auth.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TDD：模拟 Redis 中两个用户的 refresh token，验证列表聚合与强制下线。
 */
@SuppressWarnings("unchecked")
class OnlineUserServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);

    private OnlineUserService service() {
        when(redis.opsForValue()).thenReturn(values);
        return new OnlineUserService(redis, userMapper);
    }

    @Test
    void listsDistinctOnlineUsersWithUsernamesAndRoles() {
        when(redis.keys("auth:refresh:*")).thenReturn(Set.of(
                "auth:refresh:uuid-a", "auth:refresh:uuid-b", "auth:refresh:uuid-a2"));
        when(values.get("auth:refresh:uuid-a")).thenReturn("101");
        when(values.get("auth:refresh:uuid-a2")).thenReturn("101");
        when(values.get("auth:refresh:uuid-b")).thenReturn("202");
        when(redis.getExpire(org.mockito.ArgumentMatchers.anyString(), eq(TimeUnit.SECONDS))).thenReturn(3600L);
        when(userMapper.findById(101L)).thenReturn(new SysUser(101L, "alice", "x", null, null, true));
        when(userMapper.findById(202L)).thenReturn(new SysUser(202L, "bob", "x", null, null, true));
        when(userMapper.findRoles(101L)).thenReturn(Set.of("HR_ADMIN"));
        when(userMapper.findRoles(202L)).thenReturn(Set.of("EMPLOYEE"));

        List<OnlineUserRow> rows = service().listOnlineUsers();

        assertThat(rows).extracting(OnlineUserRow::userId).containsExactly(101L, 202L);
        assertThat(rows).extracting(OnlineUserRow::username).containsExactly("alice", "bob");
        assertThat(rows.get(0).roles()).containsExactly("HR_ADMIN");
        assertThat(rows.get(0).loginApprox()).isNotBlank();
    }

    @Test
    void forceOfflineDeletesOnlyTokensOwnedByTargetUser() {
        when(redis.keys("auth:refresh:*")).thenReturn(Set.of(
                "auth:refresh:uuid-a", "auth:refresh:uuid-b"));
        when(values.get("auth:refresh:uuid-a")).thenReturn("101");
        when(values.get("auth:refresh:uuid-b")).thenReturn("202");

        int removed = service().forceOffline(101L);

        assertThat(removed).isEqualTo(1);
        verify(redis).delete(List.of("auth:refresh:uuid-a"));
        verify(redis, never()).delete(List.of("auth:refresh:uuid-b"));
    }

    @Test
    void degradesToEmptyWhenRedisUnavailable() {
        OnlineUserService svc = new OnlineUserService(null, userMapper);
        assertThat(svc.listOnlineUsers()).isEmpty();
        assertThat(svc.forceOffline(1L)).isZero();
    }
}
