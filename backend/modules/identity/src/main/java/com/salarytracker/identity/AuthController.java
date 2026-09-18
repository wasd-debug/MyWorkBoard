package com.salarytracker.identity;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.platform.Audit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
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
    @Operation(operationId = "register")
    public ApiResponse<AuthResponse> register(@RequestBody Credentials body, HttpServletResponse response) {
        if (body == null) throw new IllegalArgumentException("请求体不能为空");
        AuthService.AuthTokens tokens = authService.register(body.username(), body.password(), body.nickname(), body.email());
        setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.ok(new AuthResponse(tokens.accessToken(), tokens.user()));
    }

    @PostMapping("/login")
    @Audit(module = "identity", action = "login", targetType = "app_user")
    @Operation(operationId = "login")
    public ApiResponse<AuthResponse> login(@RequestBody Credentials body, HttpServletRequest request, HttpServletResponse response) {
        if (body == null) throw new IllegalArgumentException("请求体不能为空");
        AuthService.AuthTokens tokens = authService.login(body.username(), body.password(), request.getHeader("User-Agent"));
        setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.ok(new AuthResponse(tokens.accessToken(), tokens.user()));
    }

    @PostMapping("/refresh")
    @Audit(module = "identity", action = "refresh", targetType = "refresh_token")
    @Operation(operationId = "refreshAccessToken")
    public ApiResponse<AuthResponse> refresh(@CookieValue(value = REFRESH_COOKIE, required = false) String cookie,
                                                    @RequestBody(required = false) RefreshRequest body,
                                                    HttpServletResponse response) {
        String raw = cookie != null ? cookie : body == null ? null : body.refreshToken();
        AuthService.AuthTokens tokens = authService.refresh(raw);
        setRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.ok(new AuthResponse(tokens.accessToken(), tokens.user()));
    }

    @PostMapping("/logout")
    @Audit(module = "identity", action = "logout", targetType = "refresh_token")
    @Operation(operationId = "logout")
    public ApiResponse<ActionResult> logout(@CookieValue(value = REFRESH_COOKIE, required = false) String cookie,
                                                    HttpServletResponse response) {
        authService.logout(cookie);
        response.addHeader("Set-Cookie", ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(Duration.ZERO).build().toString());
        return ApiResponse.ok(new ActionResult(true));
    }

    @PostMapping("/password")
    @PreAuthorize("isAuthenticated()")
    @Audit(module = "identity", action = "password.change", targetType = "app_user")
    @Operation(operationId = "changePassword")
    public ApiResponse<ActionResult> changePassword(@RequestBody PasswordChange body) {
        authService.changePassword(body.currentPassword(), body.newPassword());
        return ApiResponse.ok(new ActionResult(true));
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

    public record AuthResponse(String accessToken, CurrentUser user) {
    }

    public record ActionResult(boolean success) {
    }
}
