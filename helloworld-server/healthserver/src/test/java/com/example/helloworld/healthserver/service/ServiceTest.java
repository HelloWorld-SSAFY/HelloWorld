package com.example.helloworld.healthserver.service;

import com.example.helloworld.healthserver.alarm.service.FcmService;
import com.example.helloworld.healthserver.client.AiServerClient;
import com.example.helloworld.healthserver.config.UserPrincipal;
import com.example.helloworld.healthserver.dto.HealthDtos;
import com.example.helloworld.healthserver.entity.HealthData;
import com.example.helloworld.healthserver.persistence.HealthDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class HealthDataServiceTest {

    @InjectMocks
    private HealthDataService healthDataService;

    @Mock
    private HealthDataRepository healthDataRepository;

    @Mock
    private AiServerClient aiServerClient;

    @Mock
    private FcmService fcmService;

    @BeforeEach
    void setUp() {
        // HealthDataService의 @Value 주입 필드에 테스트용 값 세팅
        ReflectionTestUtils.setField(healthDataService, "aiAppToken", "TEST_APP_TOKEN");
        // 필요하면 타임존도 세팅 가능
        ReflectionTestUtils.setField(healthDataService, "appZone", "Asia/Seoul");
    }

    @Test
    @DisplayName("정상: AI 서버가 normal 모드면 FCM을 보내지 않는다")
    void createAndCheck_NormalMode_ShouldNotSendFcm() {
        // given
        var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_USER"));
        UserPrincipal testUser = new UserPrincipal(100L, 1L, authorities);
        HealthDtos.CreateRequest request = new HealthDtos.CreateRequest(null, 0.2, 80);

        AiServerClient.AnomalyResponse normalResponse = new AiServerClient.AnomalyResponse(
                true, false, "low", "normal", null, null, null, null, null
        );

        // 3-인자 시그니처에 맞춰 stubbing
        Mockito.when(aiServerClient.checkTelemetry(
                anyLong(), any(AiServerClient.TelemetryRequest.class))
        ).thenReturn(normalResponse);

        // when
        AiServerClient.AnomalyResponse actual = healthDataService.createAndCheckHealthData(testUser, request);

        // then
        Mockito.verify(healthDataRepository, Mockito.times(1)).save(any(HealthData.class));
        Mockito.verify(aiServerClient, Mockito.times(1))
                .checkTelemetry(eq(1L), any(AiServerClient.TelemetryRequest.class));
        Mockito.verify(fcmService, Mockito.never()).sendEmergencyNotification(anyLong(), anyInt());

        assertThat(actual).isEqualTo(normalResponse);
        assertThat(actual.mode()).isEqualTo("normal");
    }

    @Test
    @DisplayName("위험: AI 서버가 emergency 모드면 FCM을 보낸다")
    void createAndCheck_EmergencyMode_ShouldSendFcm() {
        // given
        var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_USER"));
        UserPrincipal testUser = new UserPrincipal(100L, 1L, authorities);
        HealthDtos.CreateRequest request = new HealthDtos.CreateRequest(null, 0.8, 130);

        AiServerClient.AnomalyResponse emergencyResponse = new AiServerClient.AnomalyResponse(
                true, true, "critical", "emergency", null, null, null, null, null
        );

        Mockito.when(aiServerClient.checkTelemetry(
                anyLong(), any(AiServerClient.TelemetryRequest.class))
        ).thenReturn(emergencyResponse);

        // when
        healthDataService.createAndCheckHealthData(testUser, request);

        // then
        Mockito.verify(healthDataRepository, Mockito.times(1)).save(any(HealthData.class));
        Mockito.verify(aiServerClient, Mockito.times(1))
                .checkTelemetry( eq(1L), any(AiServerClient.TelemetryRequest.class));
        Mockito.verify(fcmService, Mockito.times(1))
                .sendEmergencyNotification(anyLong(), anyInt());

        // 상세 인자 캡처
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> hrCaptor = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(fcmService).sendEmergencyNotification(userIdCaptor.capture(), hrCaptor.capture());

        assertThat(userIdCaptor.getValue()).isEqualTo(100L);
        assertThat(hrCaptor.getValue()).isEqualTo(130);
    }
}
