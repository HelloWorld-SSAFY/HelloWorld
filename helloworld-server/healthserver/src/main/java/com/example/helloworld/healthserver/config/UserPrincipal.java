package com.example.helloworld.healthserver.config;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
public class UserPrincipal implements UserDetails {
    private final Long userId;
    private final Long coupleId;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long userId, Long coupleId) {
        this.userId = userId;
        this.coupleId = coupleId;
        this.authorities = Collections.emptyList();
    }


    // UserDetails 인터페이스의 나머지 메소드 구현 (생략)
    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return String.valueOf(this.userId); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
