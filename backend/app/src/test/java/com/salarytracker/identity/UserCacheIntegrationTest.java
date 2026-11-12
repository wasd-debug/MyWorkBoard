package com.salarytracker.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 请求期身份缓存（AuthService.userCache）的行为约束：
 * TTL 内不回库、invalidateUser 立即生效、登录/注册路径始终读到最新状态。
 */
class UserCacheIntegrationTest extends MySqlIntegrationTestSupport {

    @Test
    void servesIdentityFromCacheUntilInvalidated() {
        AuthService auth = service();
        CurrentUser registered = auth.register("cache-" + suffix(), "password123", "缓存测试", null).user();

        assertEquals("缓存测试", auth.loadUser(registered.id()).nickname());

        jdbc.update("UPDATE app_user SET nickname = '改名之后' WHERE id = ?", registered.id());

        // 缓存期内仍返回旧值，说明这次没有回库
        assertEquals("缓存测试", auth.loadUser(registered.id()).nickname());
        // 登录路径必须绕过缓存
        assertEquals("改名之后", auth.loadUserFresh(registered.id()).nickname());
        // 显式失效后立即可见
        auth.invalidateUser(registered.id());
        assertEquals("改名之后", auth.loadUser(registered.id()).nickname());
    }

    @Test
    void cachesAbsentUserSoDisabledAccountDoesNotHitDatabaseEveryRequest() {
        AuthService auth = service();
        CurrentUser registered = auth.register("cache-" + suffix(), "password123", "禁用测试", null).user();

        assertNotNull(auth.loadUser(registered.id()));

        jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE id = ?", registered.id());

        // TTL 窗口内仍认为可用（这是缓存的已知代价，上限就是 TTL）
        assertNotNull(auth.loadUser(registered.id()));

        auth.invalidateUser(registered.id());
        assertNull(auth.loadUser(registered.id()));
        assertNull(auth.loadUserFresh(registered.id()));
    }

    @Test
    void keepsRoleAndPermissionAuthorities() {
        AuthService auth = service();
        CurrentUser user = auth.register("cache-" + suffix(), "password123", "权限测试", null).user();

        assertFalse(user.authorities().isEmpty());
        assertEquals(user.authorities(), auth.loadUser(user.id()).authorities());
        assertEquals(user.authorities(), auth.loadUser(user.id()).authorities());
    }

    private AuthService service() {
        JwtService jwt = new JwtService(new ObjectMapper().findAndRegisterModules(),
                "integration-test-secret-at-least-32-bytes-long", 7200);
        return new AuthService(jdbc, new BCryptPasswordEncoder(), jwt, 2592000, 30);
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
