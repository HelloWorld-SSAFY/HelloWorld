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
    public void sendEmergencyTripleAndRecord(Long alarmId, Long measuredUserId, int hr, String title, String body) {
        try {
            // 1) 본인 ANDROID/WATCH 토큰
            String androidToken = null, watchToken = null;
            var two = userClient.latestTwo(measuredUserId);
            if (two != null && two.getStatusCode().is2xxSuccessful() && two.getBody() != null) {
                androidToken = two.getBody().androidToken();
                watchToken   = two.getBody().watchToken();
            }

            // 2) 파트너 ANDROID 토큰
            Long partnerId = null;
            String partnerAndroidToken = null;
            var pid = userClient.partnerId(measuredUserId);
            if (pid != null && pid.getStatusCode().is2xxSuccessful() && pid.getBody() != null) {
                partnerId = pid.getBody().partnerId();
                var p = userClient.latestByPlatform(partnerId, "ANDROID");
                if (p != null && p.getStatusCode().is2xxSuccessful() && p.getBody() != null) {
                    partnerAndroidToken = p.getBody().token();
                }
            }

            // 3) 공통 데이터
            Map<String,String> data = Map.of(
                    "type","EMERGENCY",
                    "title", title != null ? title : "심박수 이상 감지",
                    "body",  body  != null ? body  : String.format("현재 심박수가 %dBPM을 초과했습니다. 상태를 확인해주세요.", hr)
            );

            // 4) 3건 전송 + 결과 수집
            var rMeA     = sendOne(androidToken,        data, measuredUserId, "ANDROID");
            var rMeW     = sendOne(watchToken,          data, measuredUserId, "WATCH");
            var rPartner = sendOne(partnerAndroidToken, data, partnerId,      "PARTNER_ANDROID");

            // 5) 집계 후 유저서버에 업서트
            boolean meSent = rMeA.success || rMeW.success;
            String  meMsg  = firstNonNull(rMeA.messageId, rMeW.messageId);
            String  meErr  = meSent ? null : firstNonNull(rMeA.errorCode, rMeW.errorCode, reasonIfEmpty(androidToken, watchToken));

            userClient.upsertRecipient(new UserServerClient.UpsertReq(
                    alarmId, measuredUserId, meSent ? "SENT" : "FAILED", meMsg, meErr));

            if (partnerId != null) {
                boolean pSent = rPartner.success;
                String  pMsg  = rPartner.messageId;
                String  pErr  = pSent ? null : firstNonNull(rPartner.errorCode, reasonIfEmpty(partnerAndroidToken));
                userClient.upsertRecipient(new UserServerClient.UpsertReq(
                        alarmId, partnerId, pSent ? "SENT" : "FAILED", pMsg, pErr));
            } else {
                log.warn("[FCM] partnerId not found for user={}", measuredUserId);
            }

        } catch (Exception e) {
            log.error("[FCM] sendEmergencyTripleAndRecord error alarmId={} user={}", alarmId, measuredUserId, e);
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

    private static class SendResult {
        final boolean success; final String messageId; final String errorCode;
        SendResult(boolean s, String id, String err){ this.success=s; this.messageId=id; this.errorCode=err; }
    }

    private SendResult sendOne(String token, Map<String,String> data, Long ownerUserId, String label) {
        if (token == null || token.isBlank()) {
            log.debug("[FCM] skip empty token label={} user={}", label, ownerUserId);
            return new SendResult(false, null, "NO_TOKEN");
        }
        try {
            var msg = Message.builder().putAllData(data).setToken(token).build();
            String res = FirebaseMessaging.getInstance().send(msg); // messageId
            log.info("[FCM] ok label={} user={} msgId={}", label, ownerUserId, res);
            return new SendResult(true, res, null);
        } catch (com.google.firebase.messaging.FirebaseMessagingException e) {
            var code = e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : "UNKNOWN";
            log.warn("[FCM] fail label={} user={} code={}", label, ownerUserId, code, e);
            return new SendResult(false, null, code);
        } catch (Exception e) {
            log.error("[FCM] error label={} user={}", label, ownerUserId, e);
            return new SendResult(false, null, "EXCEPTION");
        }
    }

    private static String firstNonNull(String... s){ for (var x: s) if (x!=null && !x.isBlank()) return x; return null; }
    private static String reasonIfEmpty(String... tokens){ for (var t: tokens) if (t!=null && !t.isBlank()) return null; return "NO_TOKEN"; }


}

