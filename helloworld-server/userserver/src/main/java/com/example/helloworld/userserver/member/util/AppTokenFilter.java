package com.example.helloworld.userserver.member.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// user-server
@Component
@RequiredArgsConstructor
public class AppTokenFilter extends OncePerRequestFilter {

    @Value("${app.app-token}")
    private String appToken;

    private static final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String path = req.getRequestURI();
        if (matcher.match("/api/internal/**", path)) {
            String x = req.getHeader("X-App-Token");
            if (x == null || !x.equals(appToken)) {
                res.setStatus(HttpStatus.UNAUTHORIZED.value());
                return;
            }
        }
        chain.doFilter(req, res);
    }
}

