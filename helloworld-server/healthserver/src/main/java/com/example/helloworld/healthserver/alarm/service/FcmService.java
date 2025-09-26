package com.example.helloworld.healthserver.alarm.service;

import com.example.helloworld.healthserver.client.UserServerClient;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
// package com.example.helloworld.healthserver.alarm.service;
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final UserServerClient userClient;

    @Async
    public void sendEmergencyNotification(Long currentUserId, Integer heartrate) {
        try {
            var resp = userClient.partnerLatest(currentUserId);
            if (resp == null || !resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null
                    || resp.getBody().token() == null || resp.getBody().token().isBlank()) {
                log.warn("[FCM-EMERGENCY] no partner token. user={}", currentUserId);
                return;
            }
            String token = resp.getBody().token();

            var data = Map.of(
                    "type",  "EMERGENCY",
                    "title", "심박수 이상 감지",
                    "body",  String.format("현재 심박수가 %dBPM을 초과했습니다. 상태를 확인해주세요.", heartrate)
            );
            var message = Message.builder().putAllData(data).setToken(token).build();
            String result = FirebaseMessaging.getInstance().send(message);
            log.info("[FCM-EMERGENCY] sent. user={} result={}", currentUserId, result);

        } catch (Exception e) {
            log.error("[FCM-EMERGENCY] fail. user={}", currentUserId, e);
        }
    }

    @Async
    public void sendReminderNotification(Long userId, String title, String body) {
        try {
            var resp = userClient.latestOfUser(userId);
            if (resp == null || !resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null
                    || resp.getBody().token() == null || resp.getBody().token().isBlank()) {
                log.warn("[FCM-REMINDER] no token. user={}", userId);
                return;
            }
            String token = resp.getBody().token();

            var data = Map.of("type","REMINDER","title",title,"body",body);
            var message = Message.builder().putAllData(data).setToken(token).build();
            String result = FirebaseMessaging.getInstance().send(message);
            log.info("[FCM-REMINDER] sent. user={} result={}", userId, result);

        } catch (Exception e) {
            log.error("[FCM-REMINDER] fail. user={}", userId, e);
        }
    }
}

