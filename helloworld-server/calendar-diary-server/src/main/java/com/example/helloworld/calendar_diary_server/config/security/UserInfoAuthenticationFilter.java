package com.example.helloworld.calendar_diary_server.config.security;

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

@Slf4j
@Component
//요청이 들어올 때마다 헤더를 읽어 SecurityContext에 UserPrincipal을 저장하는 필터
public class UserInfoAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-USER-ID");
        String coupleIdStr = request.getHeader("X-COUPLE-ID");
        String genderHeader = request.getHeader("X-ROLE");

        if (StringUtils.hasText(userId) && StringUtils.hasText(genderHeader)) {
            try {
                // ✨ 1. 헤더 값을 내부 Role로 변환하는 분기 처리 로직 호출
                String mappedRole = mapGenderToRole(genderHeader);

                if (mappedRole == null) {
                    log.warn("알 수 없는 Gender 헤더 값입니다: {}", genderHeader);
                    filterChain.doFilter(request, response);
                    return;
                }

                Long coupleId = StringUtils.hasText(coupleIdStr) ? Long.parseLong(coupleIdStr) : null;

                // ✨ 2. 수정된 UserPrincipal 생성자를 사용하여 객체 생성
                UserPrincipal userPrincipal = new UserPrincipal(Long.parseLong(userId), coupleId, mappedRole);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal, null, userPrincipal.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("SecurityContext에 사용자 인증 정보 저장: userId={}, coupleId={}, role={}", userId, coupleId, mappedRole);

            } catch (NumberFormatException e) {
                log.error("헤더의 사용자 정보 파싱 오류: userId={}, coupleId={}", userId, coupleIdStr, e);
            }
        }


        filterChain.doFilter(request, response);
    }

    /**
     * 외부 시스템(게이트웨이)에서 사용하는 성별/역할 값을
     * 우리 시스템 내부에서 사용하는 표준 역할("FEMALE", "MALE")로 변환합니다.
     * @param genderHeader 헤더에서 읽어온 값 (대소문자 구분 없음)
     * @return "FEMALE", "MALE" 또는 매핑되는 값이 없으면 null
     */
    private String mapGenderToRole(String genderHeader) {
        if (genderHeader == null) {
            return null;
        }

        // switch 문을 사용하여 깔끔하게 분기 처리
        switch (genderHeader.toUpperCase()) {
            case "A":
                return "FEMALE"; // Diary.AuthorRole.FEMALE.name() 과 같이 Enum을 사용하는 것이 더 안전

            case "B":
                return "MALE";   // Diary.AuthorRole.MALE.name()

            default:
                return null; // 매핑되는 역할이 없는 경우
        }
    }
}
