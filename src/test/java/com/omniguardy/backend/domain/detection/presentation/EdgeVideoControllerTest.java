package com.omniguardy.backend.domain.detection.presentation;

import com.omniguardy.backend.domain.detection.application.model.TriggerType;
import com.omniguardy.backend.domain.detection.application.model.VideoReceipt;
import com.omniguardy.backend.domain.detection.application.model.VideoTriggerContext;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveVideoInputPort;
import com.omniguardy.backend.domain.detection.presentation.mapper.DetectionPresentationMapper;
import com.omniguardy.backend.global.error.exception.BusinessException;
import com.omniguardy.backend.global.error.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EdgeVideoControllerTest {
    private final ReceiveVideoInputPort port = mock(ReceiveVideoInputPort.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EdgeVideoController(port, new DetectionPresentationMapper()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(port.receive(any(), any())).thenReturn(new VideoReceipt("event", "video.mp4", null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void defaultsToAudioWhenEventIdExists(String triggerType) throws Exception {
        mvc.perform(request(triggerType, "event")).andExpect(status().isOk());
        verify(port).receive(any(), eq(new VideoTriggerContext(
                "event", "trigger", TriggerType.AUDIO, "security-event", "2026-09-24T12:00:00+09:00")));
    }

    @ParameterizedTest
    @MethodSource("missingTriggerAndEvent")
    void rejectsMissingTriggerAndEvent(String triggerType, String eventId) throws Exception {
        mvc.perform(request(triggerType, eventId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("EVENT_ID_REQUIRED"))
                .andExpect(result -> assertInstanceOf(BusinessException.class, result.getResolvedException()));
        verifyNoInteractions(port);
    }

    static Stream<Arguments> missingTriggerAndEvent() {
        return Stream.of(null, "", " ", "\t").flatMap(triggerType ->
                Stream.of(null, "", " ", "\t").map(eventId -> Arguments.of(triggerType, eventId)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "audio-invalid", " AUDIO "})
    void rejectsInvalidTriggerType(String triggerType) throws Exception {
        mvc.perform(request(triggerType, "event"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_REQUEST"))
                .andExpect(result -> assertInstanceOf(BusinessException.class, result.getResolvedException()));
        verifyNoInteractions(port);
    }

    @ParameterizedTest
    @CsvSource({"AUDIO,AUDIO,event,security-event", "audio,AUDIO,event,security-event",
            "AuDiO,AUDIO,event,security-event", "KEYPAD,KEYPAD,,", "keypad,KEYPAD,,", "KeYpAd,KEYPAD,,"})
    void preservesValidTriggerContext(String triggerType, TriggerType expectedType,
                                     String eventId, String securityEventId) throws Exception {
        mvc.perform(request(triggerType, eventId, securityEventId)).andExpect(status().isOk());
        verify(port).receive(any(), eq(new VideoTriggerContext(
                eventId, "trigger", expectedType, securityEventId, "2026-09-24T12:00:00+09:00")));
    }

    private MockMultipartHttpServletRequestBuilder request(String triggerType, String eventId) {
        return request(triggerType, eventId, "security-event");
    }

    private MockMultipartHttpServletRequestBuilder request(String triggerType, String eventId, String securityEventId) {
        var request = multipart("/api/edge/video")
                .file(new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[]{1}))
                .param("triggerId", "trigger")
                .param("triggeredAt", "2026-09-24T12:00:00+09:00");
        if (securityEventId != null) {
            request.param("securityEventId", securityEventId);
        }
        if (triggerType != null) {
            request.param("triggerType", triggerType);
        }
        if (eventId != null) {
            request.param("eventId", eventId);
        }
        return request;
    }
}
