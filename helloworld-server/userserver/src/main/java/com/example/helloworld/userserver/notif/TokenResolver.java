package com.example.helloworld.userserver.notif;

import java.util.List;

public interface TokenResolver {
    /**
     * 주어진 사용자 ID의 "현재 활성" FCM 토큰 목록을 반환한다.
     * 구현체는 캐시/외부 서비스/인메모리 등 환경에 맞게 제공.
     */
    List<String> resolveActiveTokensForUser(Long userId);
}
