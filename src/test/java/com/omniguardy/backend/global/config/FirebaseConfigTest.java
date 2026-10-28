package com.omniguardy.backend.global.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
class FirebaseConfigTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void initializesFromExternalReadOnlyFileWithoutCallingGoogle() throws Exception {
        var path = directory.resolve("synthetic.json"); java.nio.file.Files.writeString(path, "{}");
        var config = new FirebaseConfig();
        ReflectionTestUtils.setField(config, "credentialsPath", path.toString());
        try (var credentials = org.mockito.Mockito.mockStatic(com.google.auth.oauth2.GoogleCredentials.class);
             var firebase = org.mockito.Mockito.mockStatic(com.google.firebase.FirebaseApp.class)) {
            credentials.when(() -> com.google.auth.oauth2.GoogleCredentials.fromStream(org.mockito.ArgumentMatchers.any(java.io.InputStream.class)))
                    .thenReturn(org.mockito.Mockito.mock(com.google.auth.oauth2.GoogleCredentials.class));
            firebase.when(com.google.firebase.FirebaseApp::getApps).thenReturn(java.util.List.of());
            config.initialize();
            firebase.verify(() -> com.google.firebase.FirebaseApp.initializeApp(org.mockito.ArgumentMatchers.any(com.google.firebase.FirebaseOptions.class)));
        }
    }
    @Test void prodRequiresExternalPathAndReportsMissingFile() {
        var config = new FirebaseConfig(); var env = new MockEnvironment(); env.setActiveProfiles("prod");
        ReflectionTestUtils.setField(config, "environment", env);
        var missing = assertThrows(IllegalStateException.class, config::initialize);
        assertTrue(missing.getMessage().contains("FIREBASE_CREDENTIALS_PATH"));
        ReflectionTestUtils.setField(config, "credentialsPath", "/nonexistent/test-only.json");
        assertThrows(IllegalStateException.class, config::initialize);
    }
}
