package com.example.helloworld.userserver.notif;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class InMemoryTokenResolver implements TokenResolver {

    // 데모: 애플리케이션 기동 후 동적으로 채워 넣거나 다른 API로 주입
    private final Map<Long, List<String>> store = new HashMap<>();

    @Override
    public List<String> resolveActiveTokensForUser(Long userId) {
        return store.getOrDefault(userId, Collections.emptyList());
    }

    // 테스트/운영 중 임시 주입용
    public void put(Long userId, List<String> tokens) {
        store.put(userId, tokens);
    }
}

