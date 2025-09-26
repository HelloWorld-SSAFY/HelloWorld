package com.example.helloworld.healthserver.alarm.service;

import com.example.helloworld.healthserver.client.UserServerClient;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final UserServerClient userClient;

    @Async
    public void sendEmergencyTriple(Long measuredUserId, int hr) {
        try {
            // 1) 피측정자(본인) ANDROID/WATCH 최신 1개씩
            String androidToken = null, watchToken = null;
            ResponseEntity<UserServerClient.LatestTwoResponse> twoResp = userClient.latestTwo(measuredUserId);
            if (twoResp != null && twoResp.getStatusCode().is2xxSuccessful() && twoResp.getBody() != null) {
                androidToken = twoResp.getBody().androidToken();
                watchToken   = twoResp.getBody().watchToken();
            } else {
                log.warn("[FCM] latestTwo empty user={}", measuredUserId);
            }

            // 2) 파트너 ANDROID 최신 1개
            Long partnerId = null;
            var pidResp = userClient.partnerId(measuredUserId);
            if (pidResp != null && pidResp.getStatusCode().is2xxSuccessful() && pidResp.getBody() != null) {
                partnerId = pidResp.getBody().partnerId();
            }
            String partnerAndroidToken = null;
            if (partnerId != null) {
                var pResp = userClient.latestByPlatform(partnerId, "ANDROID");
                if (pResp != null && pResp.getStatusCode().is2xxSuccessful() && pResp.getBody() != null) {
                    partnerAndroidToken = pResp.getBody().token();
                } else {
                    log.warn("[FCM] partner ANDROID token empty partnerId={}", partnerId);
                }
            } else {
                log.warn("[FCM] partnerId not found for user={}", measuredUserId);
            }

            // 3) 공통 데이터 페이로드
            Map<String,String> data = Map.of(
                    "type","EMERGENCY",
                    "title","심박수 이상 감지",
                    "body", String.format("현재 심박수가 %dBPM을 초과했습니다. 상태를 확인해주세요.", hr)
            );

            // 4) 총 3건 발송
            sendIfPresent(androidToken,        data, measuredUserId, "ANDROID");
            sendIfPresent(watchToken,          data, measuredUserId, "WATCH");
            sendIfPresent(partnerAndroidToken, data, partnerId,      "PARTNER_ANDROID");

        } catch (Exception e) {
            log.error("[FCM] sendEmergencyTriple error user={}", measuredUserId, e);
        }
    }

    // === 헬퍼: 토큰 존재 시만 전송 ===
    private void sendIfPresent(String token, Map<String,String> data, Long ownerUserId, String label) {
        if (token == null || token.isBlank()) {
            log.debug("[FCM] skip empty token label={} user={}", label, ownerUserId);
            return;
        }
        try {
            Message msg = Message.builder().putAllData(data).setToken(token).build();
            String res = FirebaseMessaging.getInstance().send(msg);
            log.info("[FCM] ok label={} user={} res={}", label, ownerUserId, res);
        } catch (com.google.firebase.messaging.FirebaseMessagingException e) {
            // 필요 시: SENDER_ID_MISMATCH 등 코드별 처리 추가 가능
            log.warn("[FCM] fail label={} user={} code={}", label, ownerUserId, e.getMessagingErrorCode(), e);
        } catch (Exception e) {
            log.error("[FCM] fail label={} user={}", label, ownerUserId, e);
        }
    }

    @Async
    public void sendReminderNotification(Long userId, String title, String body) {
        try {
            // 1) 유저의 ANDROID / WATCH 최신 1개씩 조회
            String androidToken = null, watchToken = null;
            var resp = userClient.latestTwo(userId);
            if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                androidToken = resp.getBody().androidToken();
                watchToken   = resp.getBody().watchToken();
            } else {
                log.warn("[FCM-REMINDER] latestTwo empty user={}", userId);
            }

            // 2) 공통 데이터 페이로드
            Map<String,String> data = Map.of(
                    "type", "REMINDER",
                    "title", title,
                    "body",  body
            );

            // 3) 두 군데 발송 (모바일 1, 워치 1)
            sendIfPresent(androidToken, data, userId, "ANDROID_REMINDER");
            sendIfPresent(watchToken,   data, userId, "WATCH_REMINDER");

        } catch (Exception e) {
            log.error("[FCM-REMINDER] send failed user={}", userId, e);
        }
    }
}

