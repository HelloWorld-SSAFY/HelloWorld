package com.example.helloworld.healthserver.config;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
// 클래스 이름을 UserInfoAuthenticationFilter로 변경하는 것을 권장합니다.
public class UserInfoAuthenticationFilter extends OncePerRequestFilter {

    // UserServerClient는 더 이상 여기서 필요하지 않습니다.
    // private final UserServerClient userServerClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 게이트웨이가 검증 후 추가해준 내부용 헤더를 읽습니다.
        //    (게이트웨이에서 사용하는 실제 헤더 이름으로 변경해야 할 수 있습니다.)
        String userIdStr = request.getHeader("X-USER-ID");
        String coupleIdStr = request.getHeader("X-COUPLE-ID");

        // 2. 사용자 ID 헤더가 존재하는 경우에만 인증을 처리합니다.
        if (StringUtils.hasText(userIdStr)) {
            try {
                Long userId = Long.parseLong(userIdStr);
                // coupleId는 null일 수 있습니다 (커플이 아닌 사용자).
                Long coupleId = StringUtils.hasText(coupleIdStr) ? Long.parseLong(coupleIdStr) : null;

                // 3. UserPrincipal 객체를 생성합니다.
                UserPrincipal userPrincipal = new UserPrincipal(userId, coupleId);

                // 4. SecurityContext에 인증 정보를 저장합니다.
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal, null, userPrincipal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated via Gateway Header: userId={}, coupleId={}", userId, coupleId);

            } catch (NumberFormatException e) {
                log.error("Failed to parse user info from gateway headers. userId: '{}', coupleId: '{}'", userIdStr, coupleIdStr, e);
                // 헤더 값이 잘못된 경우, 인증 컨텍스트를 비웁니다.
                SecurityContextHolder.clearContext();
            }
        }

        // 5. 다음 필터로 요청을 전달합니다.
        filterChain.doFilter(request, response);
    }
}