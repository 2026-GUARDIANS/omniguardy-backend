package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.securityevent.application.port.in.RecordKeypadVisionInputPort.Command;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.user.application.usecase.DemoUserResolver;
import com.omniguardy.backend.domain.user.domain.repository.UserRepository;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.global.error.exception.BusinessException;
import com.omniguardy.backend.domain.user.domain.error.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RecordKeypadVisionUseCaseTest {
    private final SecurityEventRepository events = mock(SecurityEventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MockEnvironment environment = new MockEnvironment().withProperty("omniguardy.demo.user-id", "1");
    private final RecordKeypadVisionUseCase useCase = new RecordKeypadVisionUseCase(events, new ObjectMapper(),
            new DemoUserResolver(users, environment));

    private Command command(String json) {
        return new Command("trigger", OffsetDateTime.now(), "video.mp4", null, null, "{}", json,
                1, 10.0, OffsetDateTime.now());
    }

    @Test void devConnectsConfiguredOwner() {
        environment.setActiveProfiles("dev");
        User user = mock(User.class);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        String id = useCase.record(command("[{\"eventType\":\"SILENT_SIGNAL\"}]"));
        verify(events).save(argThat(event -> event.getUser() == user && id.equals(event.getEventId())
                && event.isVisionAnalyzed()));
    }

    @Test void emptyVisionEventsSkipUserLookupAndEventCreation() {
        environment.setActiveProfiles("dev");
        assertNull(useCase.record(command("[]")));
        verifyNoInteractions(users, events);
    }

    @Test void missingOwnerDoesNotSaveEvent() {
        environment.setActiveProfiles("dev");
        assertThrows(BusinessException.class, () -> useCase.record(command("[{}]")));
        verifyNoInteractions(events);
    }

    @Test void prodConnectsConfiguredOwner() {
        environment.setActiveProfiles("prod");
        User user = mock(User.class);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        useCase.record(command("[{}]"));
        verify(events).save(argThat(event -> event.getUser() == user));
    }

    @Test void missingConfigurationPreventsEventCreationInDevAndProd() {
        environment.getPropertySources().remove("mockProperties");
        for (String profile : new String[]{"dev", "prod"}) {
            environment.setActiveProfiles(profile);
            assertEquals(UserErrorCode.DEMO_USER_ID_NOT_CONFIGURED,
                    assertThrows(BusinessException.class, () -> useCase.record(command("[{}]"))).getErrorCode());
            assertNull(useCase.record(command("[]")));
        }
        verifyNoInteractions(events, users);
    }
}
