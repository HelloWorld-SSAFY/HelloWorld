package com.example.helloworld.calendar_diary_server.config.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
//인증된 사용자의 정보(userId, coupleId 등)를 담을 객체
public class UserPrincipal implements UserDetails {

    private final Long userId;
    private final Long coupleId;
    private final String authorRole;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long userId, Long coupleId,String authorRole) {
        this.userId = userId;
        this.coupleId = coupleId;
        this.authorRole = authorRole;
        this.authorities = Collections.emptyList(); // 역할(Role) 기반 권한이 필요하면 여기에 추가
    }

    // UserDetails 인터페이스의 나머지 메소드 구현 (간단히 true 반환)
    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return String.valueOf(this.userId); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}