package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.application.usecase.QuerySecurityEventsUseCase;
import com.omniguardy.backend.domain.securityevent.domain.model.*;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class SecurityEventQueryTest {
    @ParameterizedTest
    @ValueSource(strings = {"HIGH", "MEDIUM", "LOW"})
    void filtersRiskInDatabaseBeforePagingAndExcludesOtherOwnersAndNullRisk(String riskLevel) {
        try (var factory = new Configuration().addAnnotatedClass(User.class).addAnnotatedClass(SecurityEvent.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:event-risk-query")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.generate_statistics", "true").buildSessionFactory();
             var session = factory.openSession()) {
            var tx = session.beginTransaction();
            var query = new QuerySecurityEventsUseCase(new SecurityEventRepositoryAdapter(
                    new JpaRepositoryFactory(session).getRepository(SpringDataSecurityEventRepository.class)));
            var owner = User.builder().email("owner@example.com").password("secret")
                    .name("owner").phoneNumber("01011111111").build();
            var other = User.builder().email("other@example.com").password("secret")
                    .name("other").phoneNumber("01022222222").build();
            var time = LocalDateTime.of(2026, 10, 1, 0, 0);
            audit(owner, time); session.persist(owner);
            audit(other, time); session.persist(other);
            for (String risk : java.util.List.of("HIGH", "MEDIUM", "LOW")) {
                for (int i = 0; i < 3; i++) {
                    var event = SecurityEvent.builder().eventId(risk + "-" + i).user(owner)
                            .status("COMPLETED").riskLevel(risk).build();
                    // Newer timestamp first; equal timestamps use descending IDs.
                    audit(event, time.plusDays(i == 0 ? 0 : 1)); session.persist(event);
                }
                var event = SecurityEvent.builder().eventId("other-" + risk).user(other)
                        .status("COMPLETED").riskLevel(risk).build();
                audit(event, time.plusDays(2)); session.persist(event);
            }
            for (String eventId : java.util.List.of("null-risk", "critical", "no-owner")) {
                var event = SecurityEvent.builder().eventId(eventId)
                        .user(eventId.equals("no-owner") ? null : owner).status("COMPLETED")
                        .riskLevel(eventId.equals("null-risk") ? null : eventId.equals("critical") ? "CRITICAL" : riskLevel)
                        .build();
                audit(event, time.plusDays(3)); session.persist(event);
            }
            session.flush(); session.clear(); factory.getStatistics().clear();

            var first = query.list(owner.getId(), 0, 2, riskLevel);
            assertEquals(java.util.List.of(riskLevel + "-2", riskLevel + "-1"),
                    first.content().stream().map(e -> e.eventId()).toList());
            assertEquals(3, first.totalElements()); assertEquals(2, first.totalPages());
            assertEquals(0, first.page()); assertEquals(2, first.size());
            assertTrue(first.first()); assertFalse(first.last());
            assertTrue(first.content().stream().allMatch(e -> riskLevel.equals(e.riskLevel()) && owner.getId().equals(e.userId())));
            assertEquals(2, factory.getStatistics().getPrepareStatementCount()); // filtered page + filtered count

            var second = query.list(owner.getId(), 1, 2, riskLevel);
            assertEquals(3, second.totalElements()); assertEquals(2, second.totalPages());
            assertFalse(second.first()); assertTrue(second.last());
            assertEquals(java.util.List.of(riskLevel + "-0"), second.content().stream().map(e -> e.eventId()).toList());
            assertTrue(query.list(owner.getId(), 2, 2, riskLevel).content().isEmpty());
            assertEquals(1, query.list(other.getId(), 0, 20, riskLevel).totalElements());
            assertEquals(0, query.list(999L, 0, 20, riskLevel).totalElements());

            var all = query.list(owner.getId(), 0, 20);
            assertEquals(11, all.totalElements());
            assertTrue(all.content().stream().anyMatch(e -> e.eventId().equals("null-risk") && e.riskLevel() == null));
            assertEquals(all, query.list(owner.getId(), 0, 20, null));
            tx.rollback();
        }
    }

    @Test void readsPagesDetailsAndEmptyResultsWithoutExtraUserQueries() {
        try (var factory = new Configuration().addAnnotatedClass(User.class).addAnnotatedClass(SecurityEvent.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:event-query")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.generate_statistics", "true").buildSessionFactory();
             var session = factory.openSession()) {
            var tx = session.beginTransaction();
            var repository = new SecurityEventRepositoryAdapter(new JpaRepositoryFactory(session)
                    .getRepository(SpringDataSecurityEventRepository.class));
            var query = new QuerySecurityEventsUseCase(repository);
            assertEquals(0, query.list(1L, 0, 20).totalElements());
            var owner = User.builder().email("owner@example.com").password("secret")
                    .name("private").phoneNumber("01011111111").build();
            audit(owner, LocalDateTime.now()); session.persist(owner);
            Long userId = owner.getId();
            for (int i = 0; i < 3; i++) {
                var event = SecurityEvent.builder().eventId("event-" + i).user(owner)
                        .triggerType(i == 0 ? EventTriggerType.AUDIO : EventTriggerType.KEYPAD)
                        .status("COMPLETED").visionPrediction("violence").riskLevel("HIGH")
                        .riskScore(90).agentReason("Detected threat").build();
                // Same timestamp verifies deterministic ID tie-breaking.
                audit(event, LocalDateTime.of(2026, 10, i == 0 ? 1 : 2, 0, 0));
                session.persist(event);
            }
            var other = User.builder().email("other@example.com").password("secret")
                    .name("private").phoneNumber("01022222222").build();
            audit(other, LocalDateTime.now()); session.persist(other);
            for (String eventId : java.util.List.of("other-event", "no-owner")) {
                var event = SecurityEvent.builder().eventId(eventId).status("COMPLETED")
                        .user(eventId.equals("other-event") ? other : null).build();
                audit(event, LocalDateTime.now()); session.persist(event);
            }
            session.flush(); session.clear(); factory.getStatistics().clear();
            var first = query.list(userId, 0, 2);
            assertEquals(3, first.totalElements()); assertEquals(2, first.totalPages());
            assertEquals(2, first.content().size()); assertTrue(first.first()); assertFalse(first.last());
            assertEquals("event-2", first.content().getFirst().eventId());
            assertEquals("event-1", first.content().getLast().eventId());
            assertEquals(2, factory.getStatistics().getPrepareStatementCount()); // page + count, no N+1
            assertNotNull(first.content().getFirst().userId());
            var second = query.list(userId, 1, 2);
            assertTrue(second.last()); assertEquals(EventTriggerType.AUDIO, second.content().getFirst().triggerType());
            var detail = query.detail(userId, "event-2");
            assertEquals(EventTriggerType.KEYPAD, detail.triggerType());
            assertEquals("HIGH", detail.riskLevel()); assertEquals(90, detail.riskScore());
            assertEquals("Detected threat", detail.agentReason()); assertEquals("violence", detail.visionPrediction());
            assertThrows(BusinessException.class, () -> query.detail(userId, "missing"));
            assertThrows(BusinessException.class, () -> query.detail(userId, "other-event"));
            assertThrows(BusinessException.class, () -> query.detail(userId, "no-owner"));
            assertEquals("other-event", query.list(other.getId(), 0, 20).content().getFirst().eventId());
            assertEquals(1, query.list(other.getId(), 0, 20).totalElements());
            assertEquals(0, query.list(999L, 0, 20).totalElements());
            assertThrows(BusinessException.class, () -> query.list(null, 0, 20));
            assertThrows(BusinessException.class, () -> query.detail(null, "event-2"));
            assertTrue(query.list(userId, 10, 20).content().isEmpty());
            assertThrows(BusinessException.class, () -> query.list(userId, -1, 20));
            assertThrows(BusinessException.class, () -> query.list(userId, 0, 0));
            assertThrows(BusinessException.class, () -> query.list(userId, 0, 101));
            var json = new tools.jackson.databind.ObjectMapper().writeValueAsString(detail);
            assertFalse(json.contains("password")); assertFalse(json.contains("email"));
            assertFalse(json.contains("phoneNumber")); assertFalse(json.contains("refreshToken"));
            assertFalse(json.contains("audioPath")); assertFalse(json.contains("videoPath"));
            var unlinked = com.omniguardy.backend.domain.securityevent.application.model.SecurityEventResponse.from(
                    SecurityEvent.builder().eventId("unlinked").status("CAMERA_REQUESTED").build());
            assertNull(unlinked.userId()); assertNull(unlinked.riskScore());
            tx.rollback();
        }
    }
    @Test void dtoCoversEveryPersistentEventFieldExceptSensitiveStoragePaths() {
        var expected = new java.util.HashSet<String>();
        for (Class<?> type = SecurityEvent.class; type != Object.class; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.persistence.Column.class)
                        || field.isAnnotationPresent(jakarta.persistence.Id.class)
                        || field.isAnnotationPresent(jakarta.persistence.ManyToOne.class)) {
                    expected.add(field.getName().equals("user") ? "userId" : field.getName());
                }
            }
        }
        expected.removeAll(java.util.Set.of("audioPath", "videoPath"));
        var actual = java.util.Arrays.stream(
                com.omniguardy.backend.domain.securityevent.application.model.SecurityEventResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertEquals(expected, actual);
    }
    private void audit(Object entity, LocalDateTime time) {
        ReflectionTestUtils.setField(entity, "createdAt", time);
        ReflectionTestUtils.setField(entity, "updatedAt", time);
    }
}
