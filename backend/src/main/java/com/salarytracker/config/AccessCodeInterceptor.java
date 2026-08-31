package com.salarytracker.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 可选访问口令拦截器
 * 设置 app.access-code（环境变量 ACCESS_CODE）后，
 * 除 /api/health 与 OPTIONS 预检外，所有 /api/* 请求均需携带 X-Access-Code 请求头。
 */
@Component
public class AccessCodeInterceptor implements HandlerInterceptor {

    private final String accessCode;

    public AccessCodeInterceptor(@Value("${app.access-code:}") String accessCode) {
        this.accessCode = accessCode == null ? "" : accessCode.trim();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (accessCode.isEmpty()) {
            return true;
        }
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || "/api/health".equals(path)) {
            return true;
        }
        String code = request.getHeader("X-Access-Code");
        if (!accessCode.equals(code == null ? "" : code)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write("{\"error\":\"访问口令错误或缺失\",\"needCode\":true}".getBytes(StandardCharsets.UTF_8));
            return false;
        }
        return true;
    }
}
