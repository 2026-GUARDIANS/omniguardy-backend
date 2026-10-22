package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.user.domain.model.User;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class SecurityEventUserPersistenceTest {
    @Test void userRelationshipSurvivesSaveAndReload() {
        try (var factory = new Configuration()
                .addAnnotatedClass(User.class).addAnnotatedClass(SecurityEvent.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:event-user;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .buildSessionFactory(); var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            User user = User.builder().email("demo@example.com").password("test")
                    .name("demo").phoneNumber("01012345678").build();
            initializeAuditFields(user);
            session.persist(user);
            SecurityEvent event = SecurityEvent.builder().eventId("persisted-event")
                    .user(user).status("VISION_ANALYZED").visionAnalysisMode("exhibition_rules").build();
            initializeAuditFields(event);
            session.persist(event);
            transaction.commit();
            session.clear();
            var loaded = session.find(SecurityEvent.class, event.getId());
            assertNotSame(event, loaded);
            assertEquals(user.getId(), loaded.getUser().getId());
            assertEquals("demo@example.com", loaded.getUser().getEmail());
            assertEquals("exhibition_rules", loaded.getVisionAnalysisMode());
        }
    }

    private void initializeAuditFields(Object entity) {
        // This isolated Hibernate test does not start Spring's JPA auditing infrastructure.
        ReflectionTestUtils.setField(entity, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(entity, "updatedAt", LocalDateTime.now());
    }
}
