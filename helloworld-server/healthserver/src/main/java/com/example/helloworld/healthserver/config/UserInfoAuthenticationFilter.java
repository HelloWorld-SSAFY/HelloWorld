package com.example.helloworld.healthserver.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 게이트웨이가 넣어주는 내부 헤더(X-Internal-*)를 읽어 인증 컨텍스트를 구성.
 * 프로브/메트릭/스웨거/오픈API 경로는 필터를 우회(bypass)한다.
 */
@Slf4j
@Component
public class UserInfoAuthenticationFilter extends OncePerRequestFilter {

    private static boolean isBypassPath(HttpServletRequest request) {
        // 컨텍스트 경로 고려: request.getRequestURI()는 보통 컨텍스트 포함
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        // ✅ readiness/liveness/prometheus 포함 전부 우회
        return uri.equals("/actuator/health")
                || uri.startsWith("/actuator/")
                || uri.equals("/swagger-ui.html")
                || uri.startsWith("/swagger-ui/")
                || uri.equals("/v3/api-docs")
                || uri.startsWith("/v3/api-docs/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // 1) 우회 경로는 인증 검사 없이 통과
        if (isBypassPath(request)) {
            chain.doFilter(request, response);
            return;
        }

        // 2) 내부 헤더 기반 인증
        String userIdStr   = request.getHeader("X-Internal-User-Id");
        String coupleIdStr = request.getHeader("X-Internal-Couple-Id");
        String role        = request.getHeader("X-Internal-Role");

        if (StringUtils.hasText(userIdStr)) {
            try {
                Long userId   = Long.parseLong(userIdStr);
                Long coupleId = StringUtils.hasText(coupleIdStr) ? Long.parseLong(coupleIdStr) : null;

                // ★ 프로젝트의 UserPrincipal 구현을 사용하세요.
                UserPrincipal principal = new UserPrincipal(userId, coupleId);

                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);

                log.info("HEALTH_AUDIT userId={}, coupleId={}, role={}, path={}, method={}",
                        userId, coupleId, role, request.getRequestURI(), request.getMethod());

            } catch (NumberFormatException e) {
                log.error("Invalid X-Internal-* headers: userId='{}', coupleId='{}'",
                        userIdStr, coupleIdStr, e);
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid authentication headers\"}");
                return;
            }
        } else {
            // 게이트웨이 미경유 또는 외부 호출
            log.warn("Missing X-Internal-* headers: path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Authentication required\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
