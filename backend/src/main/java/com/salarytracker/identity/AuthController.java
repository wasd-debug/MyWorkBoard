package com.salarytracker.identity;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.platform.Audit;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/auth", "/api/auth"})
public class AuthController {
    private static final String REFRESH_COOKIE = "refresh_token";
    private final AuthService authService;
    private final boolean cookieSecure;

    public AuthController(AuthService authService, @Value("${app.cookie-secure:false}") boolean cookieSecure) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/register")
    @Audit(module = "identity", action = "register", targetType = "app_user")
    public ApiResponse<Map<String, Object>> register(@RequestBody Credentials body, HttpServletResponse response) {
        if (body == null) throw new IllegalArgumentException("请求体不能为空");
        AuthService.AuthTokens tokens = authService.register(body.username(), body.password(), body.nickname(), body.email());
        setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.ok(Map.of("accessToken", tokens.accessToken(), "user", tokens.user()));
    }

    @PostMapping("/login")
    @Audit(module = "identity", action = "login", targetType = "app_user")
    public ApiResponse<Map<String, Object>> login(@RequestBody Credentials body, HttpServletRequest request, HttpServletResponse response) {
        if (body == null) throw new IllegalArgumentException("请求体不能为空");
        AuthService.AuthTokens tokens = authService.login(body.username(), body.password(), request.getHeader("User-Agent"));
        setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.ok(Map.of("accessToken", tokens.accessToken(), "user", tokens.user()));
    }

    @PostMapping("/refresh")
    @Audit(module = "identity", action = "refresh", targetType = "refresh_token")
    public ApiResponse<Map<String, Object>> refresh(@CookieValue(value = REFRESH_COOKIE, required = false) String cookie,
                                                    @RequestBody(required = false) RefreshRequest body,
                                                    HttpServletResponse response) {
        String raw = cookie != null ? cookie : body == null ? null : body.refreshToken();
        AuthService.AuthTokens tokens = authService.refresh(raw);
        setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.ok(Map.of("accessToken", tokens.accessToken(), "user", tokens.user()));
    }

    @PostMapping("/logout")
    @Audit(module = "identity", action = "logout", targetType = "refresh_token")
    public ApiResponse<Map<String, Boolean>> logout(@CookieValue(value = REFRESH_COOKIE, required = false) String cookie,
                                                    HttpServletResponse response) {
        authService.logout(cookie);
        response.addHeader("Set-Cookie", ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(Duration.ZERO).build().toString());
        return ApiResponse.ok(Map.of("loggedOut", true));
    }

    @PostMapping("/password")
    @PreAuthorize("isAuthenticated()")
    @Audit(module = "identity", action = "password.change", targetType = "app_user")
    public ApiResponse<Map<String, Boolean>> changePassword(@RequestBody PasswordChange body) {
        authService.changePassword(body.currentPassword(), body.newPassword());
        return ApiResponse.ok(Map.of("changed", true));
    }

    private void setRefreshCookie(HttpServletResponse response, String value) {
        response.addHeader("Set-Cookie", ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(Duration.ofDays(30)).build().toString());
    }

    public record Credentials(String username, String password, String nickname, String email) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record PasswordChange(String currentPassword, String newPassword) {
    }
}
