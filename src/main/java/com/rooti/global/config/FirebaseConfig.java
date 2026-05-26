package com.rooti.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.FileInputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Firebase only when explicitly enabled. {@code rooti.firebase.enabled=false} keeps the bean
 * graph small in dev and tests.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "rooti.firebase.enabled", havingValue = "true")
public class FirebaseConfig {

    @Value("${rooti.firebase.credential-json-path}")
    private String credentialJsonPath;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        try (FileInputStream serviceAccount = new FileInputStream(credentialJsonPath)) {
            FirebaseOptions options =
                    FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();
            if (FirebaseApp.getApps().isEmpty()) {
                log.info("Initializing Firebase from {}", credentialJsonPath);
                return FirebaseApp.initializeApp(options);
            }
            return FirebaseApp.getInstance();
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }
}
