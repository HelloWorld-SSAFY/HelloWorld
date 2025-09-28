package com.example.helloworld.healthserver.alarm.service;

import com.example.helloworld.healthserver.client.UserServerClient;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final UserServerClient userClient;

    // === 응급 알림 + 결과 기록 (기존 유지) ===
//    @Async
//    public void sendEmergencyTripleAndRecord(Long alarmId, Long measuredUserId, int hr, String title, String body) {
//        try {
//            // 1) 본인 ANDROID/WATCH 토큰
//            String androidToken = null, watchToken = null;
//            var two = userClient.latestTwo(measuredUserId);
//            if (two != null && two.getStatusCode().is2xxSuccessful() && two.getBody() != null) {
//                androidToken = two.getBody().androidToken();
//                watchToken   = two.getBody().watchToken();
//            }
//
//            // 2) 파트너 ANDROID 토큰
//            Long partnerId = null;
//            String partnerAndroidToken = null;
//            var pid = userClient.partnerId(measuredUserId);
//            if (pid != null && pid.getStatusCode().is2xxSuccessful() && pid.getBody() != null) {
//                partnerId = pid.getBody().partnerId();
//                var p = userClient.latestByPlatform(partnerId, "ANDROID");
//                if (p != null && p.getStatusCode().is2xxSuccessful() && p.getBody() != null) {
//                    partnerAndroidToken = p.getBody().token();
//                }
//            }
//
//            // 3) 공통 데이터 (기본 카피)
//            Map<String,String> data = Map.of(
//                    "type","EMERGENCY",
//                    "title", title != null ? title : "심박수 이상 감지",
//                    "body",  body  != null ? body  : String.format("현재 심박수가 %dBPM입니다. 상태를 확인해주세요.", hr)
//            );
//
//            // 4) 3건 전송 + 결과 수집
//            var rMeA     = sendOne(androidToken,        data, measuredUserId, "ANDROID");
//            var rMeW     = sendOne(watchToken,          data, measuredUserId, "WATCH");
//            var rPartner = sendOne(partnerAndroidToken, data, partnerId,      "PARTNER_ANDROID");
//
//            // 5) 집계 후 유저서버에 업서트
//            boolean meSent = rMeA.success || rMeW.success;
//            String  meMsg  = firstNonNull(rMeA.messageId, rMeW.messageId);
//            String  meErr  = meSent ? null : firstNonNull(rMeA.errorCode, rMeW.errorCode, reasonIfEmpty(androidToken, watchToken));
//
//            userClient.upsertRecipient(new UserServerClient.UpsertReq(
//                    alarmId, measuredUserId, meSent ? "SENT" : "FAILED", meMsg, meErr));
//
//            if (partnerId != null) {
//                boolean pSent = rPartner.success;
//                String  pMsg  = rPartner.messageId;
//                String  pErr  = pSent ? null : firstNonNull(rPartner.errorCode, reasonIfEmpty(partnerAndroidToken));
//                userClient.upsertRecipient(new UserServerClient.UpsertReq(
//                        alarmId, partnerId, pSent ? "SENT" : "FAILED", pMsg, pErr));
//            } else {
//                log.warn("[FCM] partnerId not found for user={}", measuredUserId);
//            }
//
//        } catch (Exception e) {
//            log.error("[FCM] sendEmergencyTripleAndRecord error alarmId={} user={}", alarmId, measuredUserId, e);
//        }
//    }

    // === 리마인더 발송 ===
    @Async
    public void sendReminderNotification(Long userId, String title, String body) {
        try {
            // 본인 ANDROID / WATCH
            String androidToken = null, watchToken = null;
            var resp = userClient.latestTwo(userId);
            if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                androidToken = resp.getBody().androidToken();
                watchToken   = resp.getBody().watchToken();
            } else {
                log.warn("[FCM-REMINDER] latestTwo empty user={}", userId);
            }

            // 파트너 ANDROID
            Long partnerId = null;
            String partnerAndroidToken = null;
            var pidResp = userClient.partnerId(userId);
            if (pidResp != null && pidResp.getStatusCode().is2xxSuccessful() && pidResp.getBody() != null) {
                partnerId = pidResp.getBody().partnerId();
                if (partnerId != null && !partnerId.equals(userId)) {
                    var pResp = userClient.latestByPlatform(partnerId, "ANDROID");
                    if (pResp != null && pResp.getStatusCode().is2xxSuccessful() && pResp.getBody() != null) {
                        partnerAndroidToken = pResp.getBody().token();
                    } else {
                        log.warn("[FCM-REMINDER] partner ANDROID token empty partnerId={}", partnerId);
                    }
                }
            } else {
                log.warn("[FCM-REMINDER] partnerId not found for user={}", userId);
            }

            // 공통 페이로드 (본인/파트너 동일)
            Map<String,String> data = Map.of(
                    "type",  "REMINDER",
                    "title", title,
                    "body",  body
            );

            // 발송: 본인(모바일, 워치) + 파트너(모바일)
            sendIfPresent(androidToken,        data, userId,    "ANDROID_REMINDER");
            sendIfPresent(watchToken,          data, userId,    "WATCH_REMINDER");
            sendIfPresent(partnerAndroidToken, data, partnerId, "PARTNER_ANDROID_REMINDER");

        } catch (Exception e) {
            log.error("[FCM-REMINDER] send failed user={}", userId, e);
        }
    }

    // === AI 응답을 반영해 상황별 문구/페이로드로 발송 (쿨다운 미포함 오버로드) ===
    @Async
    public void sendEmergencyTripleWithAiResponse(
            Long measuredUserId,
            int hr,
            String mode,
            String riskLevel,
            List<String> reasons
    ) {
        sendEmergencyTripleWithAiResponse(measuredUserId, hr, mode, riskLevel, reasons, null, null);
    }

    // === AI 응답을 반영해 상황별 문구/페이로드로 발송 (쿨다운 포함) ===
    @Async
    public void sendEmergencyTripleWithAiResponse(
            Long measuredUserId,
            int hr,
            String mode,                                // "restrict" | "emergency" | "normal"
            String riskLevel,                           // 필요시 사용
            List<String> reasons,                       // 예: ["HR low"], ["HR high"], ["stress high"], ["|HR_Z|>=5 x3"], ["HR>=150 for 120s"], ["HR<=45 for 120s"]
            OffsetDateTime restrictCooldownUntil,       // restrict 전용
            OffsetDateTime emergencyCooldownUntil       // emergency 전용
    ) {
        try {
            // 1) 본인 토큰
            String androidToken = null, watchToken = null;
            var twoResp = userClient.latestTwo(measuredUserId);
            if (twoResp != null && twoResp.getStatusCode().is2xxSuccessful() && twoResp.getBody() != null) {
                androidToken = twoResp.getBody().androidToken();
                watchToken   = twoResp.getBody().watchToken();
            } else {
                log.warn("[FCM] latestTwo empty user={}", measuredUserId);
            }

            // 2) 파트너 토큰
            Long partnerId = null;
            String partnerAndroidToken = null;
            var pidResp = userClient.partnerId(measuredUserId);
            if (pidResp != null && pidResp.getStatusCode().is2xxSuccessful() && pidResp.getBody() != null) {
                partnerId = pidResp.getBody().partnerId();
                var pResp = userClient.latestByPlatform(partnerId, "ANDROID");
                if (pResp != null && pResp.getStatusCode().is2xxSuccessful() && pResp.getBody() != null) {
                    partnerAndroidToken = pResp.getBody().token();
                } else {
                    log.warn("[FCM] partner ANDROID token empty partnerId={}", partnerId);
                }
            } else {
                log.warn("[FCM] partnerId not found for user={}", measuredUserId);
            }

            // 3) 이유 정규화 + 문구 생성
            String reasonCode = normalizeReason(reasons); // HR_HIGH / HR_LOW / STRESS_HIGH / HR_Z_SPIKE / HR_HIGH_120S / HR_LOW_120S / UNKNOWN
            TitleBody copy = ("emergency".equalsIgnoreCase(mode))
                    ? makeEmergencyCopy(hr, reasonCode)
                    : ("restrict".equalsIgnoreCase(mode)
                    ? makeRestrictCopy(hr, reasonCode)
                    : makeNormalCopy(hr, reasonCode, riskLevel));

            // 4) FCM 데이터 구성
            Map<String,String> selfData = new HashMap<>();
            selfData.put("type", "EMERGENCY");        // 앱 호환용
            selfData.put("mode", safe(mode));         // restrict/emergency/normal
            selfData.put("reason_code", reasonCode);  // 정규화된 이유 코드
            selfData.put("title", copy.title());
            selfData.put("body",  copy.selfBody());
            selfData.put("hr", Integer.toString(hr));
            putIfNotBlank(selfData, "restrict_cooldown_until", fmtOffset(restrictCooldownUntil));
            putIfNotBlank(selfData, "emergency_cooldown_until", fmtOffset(emergencyCooldownUntil));

            Map<String,String> partnerData = new HashMap<>(selfData);
            partnerData.put("body", copy.partnerBody());

            // 5) 본인에게 발송 (ANDROID, WATCH)
            sendIfPresent(androidToken, selfData, measuredUserId, "ANDROID");
            sendIfPresent(watchToken,   selfData, measuredUserId, "WATCH");

            // 6) restrict/emergency 모드일 때만 파트너 발송
            if ("emergency".equalsIgnoreCase(mode) || "restrict".equalsIgnoreCase(mode)) {
                sendIfPresent(partnerAndroidToken, partnerData, partnerId, "PARTNER_ANDROID");
            } else {
                log.debug("[FCM] Normal mode - skipping partner notification for user={}", measuredUserId);
            }

        } catch (Exception e) {
            log.error("[FCM] sendEmergencyTripleWithAiResponse error user={}", measuredUserId, e);
        }
    }

    // === 단순 응급 알림(기본 카피) ===
    @Async
    public void sendEmergencyTriple(Long alarmId, Long measuredUserId, int hr, String title, String body) {
        try {
            // 1) 본인 ANDROID / WATCH 최신 토큰
            String androidToken = null, watchToken = null;
            var twoResp = userClient.latestTwo(measuredUserId);
            if (twoResp != null && twoResp.getStatusCode().is2xxSuccessful() && twoResp.getBody() != null) {
                androidToken = twoResp.getBody().androidToken();
                watchToken   = twoResp.getBody().watchToken();
            } else {
                log.warn("[FCM] latestTwo empty user={}", measuredUserId);
            }

            // 2) 파트너 ANDROID 최신 토큰
            Long partnerId = null;
            String partnerAndroidToken = null;
            var pidResp = userClient.partnerId(measuredUserId);
            if (pidResp != null && pidResp.getStatusCode().is2xxSuccessful() && pidResp.getBody() != null) {
                partnerId = pidResp.getBody().partnerId();
                var pResp = userClient.latestByPlatform(partnerId, "ANDROID");
                if (pResp != null && pResp.getStatusCode().is2xxSuccessful() && pResp.getBody() != null) {
                    partnerAndroidToken = pResp.getBody().token();
                } else {
                    log.warn("[FCM] partner ANDROID token empty partnerId={}", partnerId);
                }
            } else {
                log.warn("[FCM] partnerId not found for user={}", measuredUserId);
            }

            // 3) 기본(노멀) 카피 생성 (두 인자 버전과 동일 로직)
            TitleBody copy = makeNormalCopy(hr, "UNKNOWN", "low");

            String finalTitle     = (title != null && !title.isBlank()) ? title : copy.title();
            String finalSelfBody  = (body  != null && !body.isBlank())  ? body  : copy.selfBody();
            String finalPartnBody = copy.partnerBody();

            Map<String,String> selfData = new java.util.HashMap<>();
            selfData.put("type", "EMERGENCY");
            selfData.put("mode", "normal");
            selfData.put("reason_code", "UNKNOWN");
            selfData.put("title", finalTitle);
            selfData.put("body",  finalSelfBody);
            selfData.put("hr", Integer.toString(hr));

            Map<String,String> partnerData = new java.util.HashMap<>();
            partnerData.put("type", "EMERGENCY");
            partnerData.put("mode", "normal");
            partnerData.put("reason_code", "UNKNOWN");
            partnerData.put("title", finalTitle);
            partnerData.put("body",  finalPartnBody);
            partnerData.put("hr", Integer.toString(hr));

            // 4) 전송 + 결과 수집(업서트 목적이므로 sendOne 사용)
            var rMeA = sendOne(androidToken,        selfData, measuredUserId, "ANDROID");
            var rMeW = sendOne(watchToken,          selfData, measuredUserId, "WATCH");
            var rPtn = sendOne(partnerAndroidToken, partnerData, partnerId,   "PARTNER_ANDROID");

            // 5) recipients 업서트
            boolean meSent = rMeA.success || rMeW.success;
            String  meMsg  = firstNonNull(rMeA.messageId, rMeW.messageId);
            String  meErr  = meSent ? null : firstNonNull(rMeA.errorCode, rMeW.errorCode, reasonIfEmpty(androidToken, watchToken));
            userClient.upsertRecipient(new UserServerClient.UpsertReq(
                    alarmId, measuredUserId, meSent ? "SENT" : "FAILED", meMsg, meErr
            ));

            if (partnerId != null) {
                boolean pSent = rPtn.success;
                String  pMsg  = rPtn.messageId;
                String  pErr  = pSent ? null : firstNonNull(rPtn.errorCode, reasonIfEmpty(partnerAndroidToken));
                userClient.upsertRecipient(new UserServerClient.UpsertReq(
                        alarmId, partnerId, pSent ? "SENT" : "FAILED", pMsg, pErr
                ));
            }

        } catch (Exception e) {
            log.error("[FCM] sendEmergencyTriple(5 args) error alarmId={} user={}", alarmId, measuredUserId, e);
        }
    }

    // === 토큰 존재 시만 전송 ===
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
            log.warn("[FCM] fail label={} user={} code={}", label, ownerUserId, e.getMessagingErrorCode(), e);
        } catch (Exception e) {
            log.error("[FCM] fail label={} user={}", label, ownerUserId, e);
        }
    }

    // === 단일 전송(결과 반환) ===
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

    // === 카피 생성 보조 ===
    private static String safe(String s){ return s==null ? "normal" : s; }
    private static void putIfNotBlank(Map<String,String> m, String k, String v){
        if (v != null && !v.isBlank()) m.put(k, v);
    }
    private static String fmtOffset(OffsetDateTime odt){
        return odt == null ? null : odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static record TitleBody(String title, String selfBody, String partnerBody) {}

    /** reasons 리스트를 표준 코드로 정규화 */
    private static String normalizeReason(List<String> reasons){
        if (reasons == null || reasons.isEmpty()) return "UNKNOWN";
        String joined = String.join("|", reasons).toLowerCase();

        // 스트레스 관련 패턴들 추가
        if (joined.contains("stress_z") || joined.contains("|stress_z|")) return "STRESS_HIGH";
        if (joined.contains("stress high"))                              return "STRESS_HIGH";

        // 기존 HR 패턴들
        if (joined.contains("HR low"))                                   return "HR_LOW";
        if (joined.contains("HR high"))                                  return "HR_HIGH";
        if ((joined.contains("hr>=150") || joined.contains("hr >= 150")) && joined.contains("120s")) return "HR_HIGH_120S";
        if ((joined.contains("hr<=45")  || joined.contains("hr <= 45"))  && joined.contains("120s")) return "HR_LOW_120S";
        if (joined.contains("|hr_z|>=5") || joined.contains("hr_z") || joined.contains("z spike"))   return "HR_Z_SPIKE";

        return "UNKNOWN";
    }

    /** restrict 모드 카피 */
    private static TitleBody makeRestrictCopy(int hr, String reason){
        switch (reason) {
            case "STRESS_HIGH" -> {
                String t = "스트레스 지수 높음";
                String s = "스트레스 지수가 높습니다. 추천 카테고리를 이용해 보세요!";
                String p = "산모의 스트레스 지수가 높습니다. 상태를 확인해 주세요.";
                return new TitleBody(t, s, p);
            }
            case "HR_HIGH" -> {
                String t = "심박수 상승";
                String s = String.format("심박수가 %dBPM 이상입니다. 추천 카테고리를 이용해 보세요!", hr);
                String p = String.format("산모의 심박수가 %dBPM 이상으로 높습니다. 상태를 확인해 주세요.", hr);
                return new TitleBody(t, s, p);
            }
            case "HR_LOW" -> {
                String t = "심박수 저하";
                String s = String.format("심박수가 %dBPM 이하입니다. 추천 카테고리를 이용해 보세요!", hr);
                String p = String.format("산모의 심박수가 %dBPM 이하로 낮습니다. 상태를 확인해 주세요.", hr);
                return new TitleBody(t, s, p);
            }
            default -> {
                String t = "위험 감지";
                String s = String.format("위험이 감지되었습니다!");
//                String s = String.format("현재 상태로 제한 모드가 적용되었습니다. (심박수 %dBPM)", hr);
                String p = "산모에게 위험이 감지되었습니다!";
                return new TitleBody(t, s, p);
            }
        }
    }

    /** emergency 모드 카피 */
    private static TitleBody makeEmergencyCopy(int hr, String reason){
        switch (reason) {
            case "HR_Z_SPIKE" -> {
                String t = "🚨 급격한 심박수 변동 감지";
                String s = String.format("심박수가 급격히 변했습니다. (현재 %dBPM) 안전한 곳에서 즉시 휴식하세요.", hr);
                String p = String.format("파트너의 심박수에 급격한 변동이 감지되었습니다. (현재 %dBPM) 즉시 연락하여 상태를 확인하세요.", hr);
                return new TitleBody(t, s, p);
            }
            case "HR_HIGH_120S" -> {
                String t = "🚨 심박수 매우 높음 (2분 지속)";
                String s = String.format("심박수 높음 상태가 120초 이상 지속되었습니다. (현재 %dBPM) 즉시 휴식하고 필요시 응급실에 연락하세요.", hr);
                String p = String.format("파트너의 심박수가 2분 이상 매우 높은 상태입니다. (현재 %dBPM) 바로 연락해 상태를 확인하세요.", hr);
                return new TitleBody(t, s, p);
            }
            case "HR_LOW_120S" -> {
                String t = "🚨 심박수 매우 낮음 (2분 지속)";
                String s = String.format("심박수 낮음 상태가 120초 이상 지속되었습니다. (현재 %dBPM) 어지럼증 등 증상을 확인하고 필요시 응급실에 연락하세요.", hr);
                String p = String.format("파트너의 심박수가 2분 이상 매우 낮습니다. (현재 %dBPM) 즉시 상태를 확인하세요.", hr);
                return new TitleBody(t, s, p);
            }
            case "HR_HIGH" -> {
                String t = "🚨 심박수 위험 수치";
                String s = String.format("현재 심박수가 매우 높습니다. (현재 %dBPM) 즉시 휴식이 필요합니다.", hr);
                String p = String.format("파트너의 심박수가 매우 높습니다. (현재 %dBPM) 즉시 연락해 주세요.", hr);
                return new TitleBody(t, s, p);
            }
            case "HR_LOW" -> {
                String t = "🚨 심박수 위험 저하";
                String s = String.format("현재 심박수가 매우 낮습니다. (현재 %dBPM) 안전을 위해 즉시 조치하세요.", hr);
                String p = String.format("파트너의 심박수가 매우 낮습니다. (현재 %dBPM) 즉시 상태를 확인하세요.", hr);
                return new TitleBody(t, s, p);
            }
            default -> {
                String t = "🚨 응급 상황 감지";
                String s = String.format("응급 상황이 감지되었습니다. (현재 %dBPM) 즉시 안전 조치를 취하세요.", hr);
                String p = String.format("파트너에게 응급 상황이 감지되었습니다. (현재 %dBPM) 바로 연락하세요.", hr);
                return new TitleBody(t, s, p);
            }
        }
    }

    /** normal (fallback) */
    private static TitleBody makeNormalCopy(int hr, String reason, String riskLevel){
        String t = "💗 심박수 알림";
        String s = String.format("현재 심박수는 %dBPM입니다. 상태를 확인하세요.", hr);
        String p = String.format("파트너의 심박수는 %dBPM입니다.", hr);
        if ("high".equalsIgnoreCase(riskLevel)) {
            t = "⚠️ 심박수 주의";
            s = String.format("현재 심박수 %dBPM, 주의가 필요합니다. 잠시 휴식을 취하세요.", hr);
            p = String.format("파트너의 심박수가 %dBPM으로 높습니다. 상태를 확인해 주세요.", hr);
        }
        return new TitleBody(t, s, p);
    }

    // === 내부 클래스들 ===
    private static class SendResult {
        final boolean success;
        final String messageId;
        final String errorCode;
        SendResult(boolean s, String id, String err){ this.success=s; this.messageId=id; this.errorCode=err; }
    }

    // === 공통 헬퍼들 ===
    private static String firstNonNull(String... s){
        for (var x: s) if (x!=null && !x.isBlank()) return x;
        return null;
    }
    private static String reasonIfEmpty(String... tokens){
        for (var t: tokens) if (t!=null && !t.isBlank()) return null;
        return "NO_TOKEN";
    }


    @Async
    public void sendRestrictFromSteps(Long measuredUserId, List<String> reasons) {
        try {
            // 본인 ANDROID / WATCH 최신 토큰
            String androidToken = null, watchToken = null;
            var two = userClient.latestTwo(measuredUserId);
            if (two != null && two.getStatusCode().is2xxSuccessful() && two.getBody() != null) {
                androidToken = two.getBody().androidToken();
                watchToken   = two.getBody().watchToken();
            } else {
                log.warn("[FCM-RESTRICT-STEPS] latestTwo empty user={}", measuredUserId);
            }

            // 파트너 ANDROID 최신 토큰
            Long partnerId = null;
            String partnerAndroidToken = null;
            var pid = userClient.partnerId(measuredUserId);
            if (pid != null && pid.getStatusCode().is2xxSuccessful() && pid.getBody() != null) {
                partnerId = pid.getBody().partnerId();
                var p = userClient.latestByPlatform(partnerId, "ANDROID");
                if (p != null && p.getStatusCode().is2xxSuccessful() && p.getBody() != null) {
                    partnerAndroidToken = p.getBody().token();
                } else {
                    log.warn("[FCM-RESTRICT-STEPS] partner ANDROID token empty partnerId={}", partnerId);
                }
            } else {
                log.warn("[FCM-RESTRICT-STEPS] partnerId not found for user={}", measuredUserId);
            }

            // 알림 내용
            String title = "활동 알림";
            String body  =  "활동량이 저조합니다! 추천 카테고리를 확인해 주세요.";

            // 공통 데이터
            Map<String,String> data = new java.util.HashMap<>();
            data.put("type", "RESTRICT");
            data.put("source", "STEPS");
            data.put("title", title);
            data.put("body",  body);
            if (reasons != null && !reasons.isEmpty()) {
                data.put("reasons", String.join("|", reasons));
            }

            // 본인(ANDROID, WATCH)
            sendIfPresent(androidToken, data, measuredUserId, "ANDROID_RESTRICT_STEPS");
            sendIfPresent(watchToken,   data, measuredUserId, "WATCH_RESTRICT_STEPS");

            // 파트너(ANDROID)
            sendIfPresent(partnerAndroidToken, data, partnerId, "PARTNER_ANDROID_RESTRICT_STEPS");

        } catch (Exception e) {
            log.error("[FCM-RESTRICT-STEPS] send failed user={}", measuredUserId, e);
        }
    }
}
