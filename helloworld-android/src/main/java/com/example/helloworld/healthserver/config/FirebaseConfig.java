package com.example.helloworld.healthserver.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.InputStream;


@Slf4j
@Component
public class FirebaseConfig {

    private static final String CREDENTIALS_CLASSPATH = "firebase-service-account.json";

    @Bean
    public FirebaseApp firebaseApp() throws Exception {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        // 1) classpath: src/main/resources/firebase-service-account.json
        InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(CREDENTIALS_CLASSPATH);

        if (in == null) {
            throw new IllegalStateException(
                    "클래스패스에서 " + CREDENTIALS_CLASSPATH + " 파일을 찾지 못했습니다. " +
                            "src/main/resources/" + CREDENTIALS_CLASSPATH + " 로 위치를 확인하세요."
            );
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(in))
                .build();

        return FirebaseApp.initializeApp(options);
    }
}
