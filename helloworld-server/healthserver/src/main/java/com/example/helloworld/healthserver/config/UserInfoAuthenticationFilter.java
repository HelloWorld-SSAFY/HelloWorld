package com.example.helloworld.healthserver.config;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class UserInfoAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 게이트웨이와 동일한 헤더 이름 사용
        String userIdStr = request.getHeader("X-Internal-User-Id");
        String coupleIdStr = request.getHeader("X-Internal-Couple-Id");
        String role = request.getHeader("X-Internal-Role");

        if (StringUtils.hasText(userIdStr)) {
            try {
                Long userId = Long.parseLong(userIdStr);
                Long coupleId = StringUtils.hasText(coupleIdStr) ?
                        Long.parseLong(coupleIdStr) : null;

                UserPrincipal userPrincipal = new UserPrincipal(userId, coupleId);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userPrincipal, null, userPrincipal.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 헬스케어 컴플라이언스를 위한 감사 로그
                log.info("HEALTH_AUDIT: userId={}, coupleId={}, role={}, path={}, method={}",
                        userId, coupleId, role, request.getRequestURI(), request.getMethod());

            } catch (NumberFormatException e) {
                log.error("Failed to parse user info: userId='{}', coupleId='{}'",
                        userIdStr, coupleIdStr, e);
                SecurityContextHolder.clearContext();

                // 파싱 실패 시 요청 거부
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid authentication headers\"}");
                return;
            }
        } else {
            // 인증 헤더가 없으면 요청 거부
            log.warn("Missing authentication headers for path: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Authentication required\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}