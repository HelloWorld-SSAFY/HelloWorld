package com.example.helloworld.healthserver.controller;

import com.example.helloworld.healthserver.client.AiServerClient;
import com.example.helloworld.healthserver.config.UserInfoAuthenticationFilter;
import com.example.helloworld.healthserver.config.UserPrincipal;
import com.example.helloworld.healthserver.service.HealthDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ✨ 1. Use MockitoExtension instead of relying on Spring's test context
@ExtendWith(MockitoExtension.class)
class WearableHealthControllerTest {

    // ✨ 2. MockMvc is now configured manually
    private MockMvc mockMvc;

    // ✨ 3. Use standard Mockito annotations
    @Mock
    private HealthDataService healthDataService;

    @InjectMocks
    private WearableHealthController wearableHealthController;

    // ObjectMapper is no longer injected by Spring, so we create it ourselves.
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ✨ 4. Set up MockMvc before each test
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(wearableHealthController)
                // We must manually add the filter that @WebMvcTest used to add automatically.
                .addFilter(new UserInfoAuthenticationFilter())
                .build();
    }

    // 테스트용 인증 객체를 생성하는 헬퍼 메소드
    private Authentication createTestAuth() {
        UserPrincipal testUser = new UserPrincipal(100L, 1L);
        return new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
    }

    @Test
    @DisplayName("POST /api/wearable - 이상 징후 감지 시 AI 응답을 그대로 반환한다")
    void createAndCheck_WhenAnomalyDetected_ReturnsAiResponse() throws Exception {
        // given (준비)
        String requestJson = """
                {
                    "heartrate": 130,
                    "stress": 0.8
                }
                """;

        AiServerClient.AnomalyResponse emergencyResponse = new AiServerClient.AnomalyResponse(
                true, true, "critical", "emergency", null, null, null, null, null
        );
        // The rest of the test logic remains exactly the same!
        when(healthDataService.createAndCheckHealthData(any(UserPrincipal.class), any()))
                .thenReturn(emergencyResponse);

        // when & then (실행 및 검증)
        mockMvc.perform(post("/api/wearable")
                        .with(authentication(createTestAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.anomaly").value(true))
                .andExpect(jsonPath("$.mode").value("emergency"));
    }
}