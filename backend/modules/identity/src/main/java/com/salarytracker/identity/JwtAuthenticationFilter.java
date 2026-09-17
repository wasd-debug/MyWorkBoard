package com.salarytracker.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AuthService authService;

    public JwtAuthenticationFilter(JwtService jwtService, AuthService authService) {
        this.jwtService = jwtService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            CurrentUser tokenUser = jwtService.parse(header.substring(7).trim());
            if (tokenUser != null) {
                CurrentUser current = authService.loadUser(tokenUser.id());
                if (current != null) {
                    var authorities = current.authorities().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(current, null, authorities));
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
