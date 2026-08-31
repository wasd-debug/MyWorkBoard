package com.salarytracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：访问口令拦截 + CORS
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AccessCodeInterceptor accessCodeInterceptor;

    public WebConfig(AccessCodeInterceptor accessCodeInterceptor) {
        this.accessCodeInterceptor = accessCodeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessCodeInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "PUT", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Access-Code")
                .maxAge(3600);
    }
}
