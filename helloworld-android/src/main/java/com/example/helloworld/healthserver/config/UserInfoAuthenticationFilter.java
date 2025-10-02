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

        // 이 필터의 역할: 내부 헤더가 있으면 인증 정보를 SecurityContext에 채워넣는다.
        // 요청을 막거나 허용하는 결정은 SecurityConfig에서 처리하도록 위임한다.
        final String userIdStr   = request.getHeader("X-Internal-User-Id");
        final String coupleIdStr = request.getHeader("X-Internal-Couple-Id");

        if (StringUtils.hasText(userIdStr)) {
            try {
                Long userId   = Long.parseLong(userIdStr);
                Long coupleId = StringUtils.hasText(coupleIdStr) ? Long.parseLong(coupleIdStr) : null;

                var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_USER"));
                UserPrincipal principal = new UserPrincipal(userId, coupleId, authorities);
                var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);

                log.info("HEALTH_AUDIT: Authenticated via internal header. userId={}, coupleId={}, path={}",
                        userId, coupleId, request.getRequestURI());

            } catch (NumberFormatException e) {
                log.warn("Invalid number format in internal headers. userId='{}', coupleId='{}'", userIdStr, coupleIdStr);
                // 컨텍스트를 비우고 체인을 계속 진행. 이후 인가 단계에서 접근이 거부될 것임.
                SecurityContextHolder.clearContext();
            }
        }

        // 항상 필터 체인을 계속 진행시킨다.
        chain.doFilter(request, response);
    }


}
