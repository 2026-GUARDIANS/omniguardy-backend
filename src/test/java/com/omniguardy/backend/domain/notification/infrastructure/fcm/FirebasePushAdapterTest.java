package com.omniguardy.backend.domain.notification.infrastructure.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.omniguardy.backend.domain.notification.domain.error.NotificationErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirebasePushAdapterTest {
    private final FirebasePushAdapter adapter = new FirebasePushAdapter();
    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);

    @ParameterizedTest
    @ValueSource(strings = {"2", "3"})
    void fcmMessageIncludesPublicEventIdAndPreservesTypeAndNumericRisk(String riskLevel) throws Exception {
        try (var firebase = mockStatic(FirebaseMessaging.class)) {
            firebase.when(FirebaseMessaging::getInstance).thenReturn(messaging);

            adapter.send("android-token", "title", "body", "SECURITY_EVENT", riskLevel, "evt-video-20261029-001");

            var sent = ArgumentCaptor.forClass(Message.class);
            verify(messaging).send(sent.capture());
            Message message = sent.getValue();
            assertEquals(Map.of("type", "SECURITY_EVENT", "riskLevel", riskLevel,
                    "eventId", "evt-video-20261029-001"), ReflectionTestUtils.getField(message, "data"));
            assertEquals("android-token", ReflectionTestUtils.getField(message, "token"));
            var notification = ReflectionTestUtils.getField(message, "notification");
            assertEquals("title", ReflectionTestUtils.getField(notification, "title"));
            assertEquals("body", ReflectionTestUtils.getField(notification, "body"));
        }
    }

    @Test
    void existingTestPushOmitsEventIdInsteadOfSendingNullOrInventedId() throws Exception {
        try (var firebase = mockStatic(FirebaseMessaging.class)) {
            firebase.when(FirebaseMessaging::getInstance).thenReturn(messaging);

            adapter.send("android-token", "title", "body", "SECURITY_EVENT", "2", null);

            var sent = ArgumentCaptor.forClass(Message.class);
            verify(messaging).send(sent.capture());
            assertEquals(Map.of("type", "SECURITY_EVENT", "riskLevel", "2"),
                    ReflectionTestUtils.getField(sent.getValue(), "data"));
        }
    }

    @Test
    void deliveryFailureUsesExistingBusinessError() throws Exception {
        try (var firebase = mockStatic(FirebaseMessaging.class)) {
            firebase.when(FirebaseMessaging::getInstance).thenReturn(messaging);
            var failure = new IllegalStateException("FCM unavailable");
            when(messaging.send(any(Message.class))).thenThrow(failure);

            var exception = assertThrows(BusinessException.class,
                    () -> adapter.send("android-token", "title", "body", "SECURITY_EVENT", "3", "evt-public"));

            assertEquals(NotificationErrorCode.PUSH_DELIVERY_FAILED, exception.getErrorCode());
            assertSame(failure, exception.getCause());
        }
    }
}
