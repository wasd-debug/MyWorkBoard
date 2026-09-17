package com.salarytracker.identity;

import com.salarytracker.platform.UnauthorizedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {
    public CurrentUser required() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CurrentUser user) return user;
        throw new UnauthorizedException("请先登录");
    }

    public long id() {
        return required().id();
    }
}
