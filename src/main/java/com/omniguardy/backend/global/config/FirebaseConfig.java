package com.omniguardy.backend.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

@Configuration
public class FirebaseConfig {
    @org.springframework.beans.factory.annotation.Value("${firebase.credentials-path:}")
    private String credentialsPath = "";
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment environment;

    @PostConstruct
    public void initialize() {
        try {
            if (environment != null && environment.matchesProfiles("prod") && credentialsPath.isBlank()) {
                throw new IllegalStateException("FIREBASE_CREDENTIALS_PATH must reference an external readable JSON file in prod");
            }
            try (InputStream serviceAccount = credentialsPath.isBlank() ? getClass()
                    .getClassLoader()
                    .getResourceAsStream("firebase/firebase-service-account.json")
                    : java.nio.file.Files.newInputStream(java.nio.file.Path.of(credentialsPath))) {

            if (serviceAccount == null) {
                throw new IllegalStateException("Firebase service account file not found");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            }

        } catch (Exception e) {
            throw new IllegalStateException("Firebase initialization failed: check FIREBASE_CREDENTIALS_PATH and readable valid credentials", e);
        }
    }
}
