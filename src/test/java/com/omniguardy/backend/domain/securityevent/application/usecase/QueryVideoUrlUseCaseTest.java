package com.omniguardy.backend.domain.securityevent.application.usecase;
import com.omniguardy.backend.domain.securityevent.application.port.out.VideoUrlPort;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryVideoUrlUseCaseTest {
    @Test void signsOnlyOwnedVideoAndHidesOtherUsersEvents() {
        var repository = mock(SecurityEventRepository.class); var urls = mock(VideoUrlPort.class);
        var query = new QueryVideoUrlUseCase(repository, urls);
        when(repository.findByEventIdAndUserId("event", 1L)).thenReturn(Optional.of(SecurityEvent.builder().videoPath("videos/key").build()));
        when(urls.sign("videos/key")).thenReturn("https://signed.invalid");
        assertEquals("https://signed.invalid", query.query("event", 1L));
        assertThrows(BusinessException.class, () -> query.query("event", 2L));
        verify(urls, times(1)).sign("videos/key");
    }
}
