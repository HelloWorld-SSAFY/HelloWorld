package com.example.helloworld.healthserver.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (isBypassPath(request)) {
            chain.doFilter(request, response);
            return;
        }

        String userIdStr   = request.getHeader("X-Internal-User-Id");
        String coupleIdStr = request.getHeader("X-Internal-Couple-Id");

        if (StringUtils.hasText(userIdStr)) {
            try {
                Long userId   = Long.parseLong(userIdStr);
                Long coupleId = StringUtils.hasText(coupleIdStr) ? Long.parseLong(coupleIdStr) : null;


                // 1. 먼저 권한 목록을 생성합니다.
                var authorities = Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_INTERNAL_USER")
                );

                // 2. 권한을 포함하여 UserPrincipal 객체를 생성합니다.
                UserPrincipal principal = new UserPrincipal(userId, coupleId, authorities);

                // 3. 생성된 principal을 사용하여 인증 토큰을 만듭니다.
                // 이 생성자는 principal.getAuthorities()를 호출하여 권한을 자동으로 설정합니다.
                var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(auth);


                // 🔹 로그 자리수 맞추기(예전 포맷은 role 자리에 path가 찍혔음)
                log.info("HEALTH_AUDIT userId={}, coupleId={}, path={}, method={}",
                        userId, coupleId, request.getRequestURI(), request.getMethod());

            } catch (NumberFormatException e) {
                log.error("Invalid X-Internal-* headers: userId='{}', coupleId='{}'", userIdStr, coupleIdStr, e);
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid authentication headers\"}");
                return;
            }
        } else {
            log.warn("Missing X-Internal-* headers: path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Authentication required\"}");
            return;
        }

        chain.doFilter(request, response);
    }


}
