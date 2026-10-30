package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.user.domain.model.User;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class SecurityEventUserPersistenceTest {
    @ParameterizedTest
    @EnumSource(EventTriggerType.class)
    void userRelationshipSurvivesSaveAndReload(EventTriggerType triggerType) {
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
                    .user(user).triggerType(triggerType).status("VISION_ANALYZED").visionAnalysisMode("exhibition_rules").build();
            initializeAuditFields(event);
            session.persist(event);
            transaction.commit();
            session.clear();
            var repository = new SecurityEventRepositoryAdapter(new JpaRepositoryFactory(session)
                    .getRepository(SpringDataSecurityEventRepository.class));
            var loaded = repository.findByEventId(event.getEventId()).orElseThrow();
            session.clear();
            assertNotSame(event, loaded);
            assertEquals(triggerType, loaded.getTriggerType());
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
