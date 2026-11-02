package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.auth.infrastructure.security.JwtProvider;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.visionsession.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.port.in.StreamingSessionVideoInputPort;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.presentation.*;
import com.omniguardy.backend.global.config.SecurityConfig;
import com.omniguardy.backend.global.error.exception.*;
import com.omniguardy.backend.global.error.response.ErrorResponseWriter;
import com.omniguardy.backend.global.security.auth.CustomUserDetailsService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import static com.omniguardy.backend.domain.visionsession.StreamingMp4TestSupport.*;
import static com.omniguardy.backend.domain.visionsession.domain.error.StreamingVideoErrorCode.*;
import static com.omniguardy.backend.domain.visionsession.domain.error.VisionSessionErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(StreamingVideoApiTest.Config.class) @WebAppConfiguration
@TestPropertySource(properties = {"omniguardy.device.api-key=device-key", "omniguardy.vision.callback.api-key=vision-key",
        "jwt.secret-key=test-secret-at-least-32-bytes-long", "jwt.access-token-expire-time=1800000", "jwt.refresh-token-expire-time=1209600000"})
class StreamingVideoApiTest {
    static final String URL = "/api/edge/vision/sessions/session/video";
    @Autowired WebApplicationContext context;
    @Autowired StreamingSessionVideoInputPort videos;
    MockMvc mvc;
    @BeforeEach void setup() {
        reset(videos);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    StreamingVideoView view() {
        return new StreamingVideoView("session", VisionTriggerType.KEYPAD, "trigger", null, StreamingSessionVideo.State.STORED,
                file(1).sha256(), mp4(1).length, "recording.mp4", "stored.mp4", "S3", "bucket", "s3://bucket/stored.mp4",
                Instant.parse("2026-11-02T00:00:00Z"), Instant.parse("2026-11-02T00:00:01Z"), null);
    }
    MockMultipartFile part() { return new MockMultipartFile("file", "recording.mp4", "video/mp4", mp4(1)); }
    @Test void storesWithLocationThenReplaysAndGetsMetadata() throws Exception {
        when(videos.upload(eq("session"), any())).thenReturn(new StreamingSessionVideoInputPort.Receipt(view(), true),
                new StreamingSessionVideoInputPort.Receipt(view(), false));
        mvc.perform(multipart(URL).file(part()).header("X-Device-Api-Key", "device-key"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", URL))
                .andExpect(jsonPath("code").value("STREAMING_VIDEO_STORED"))
                .andExpect(jsonPath("data.uploadState").value("STORED"))
                .andExpect(jsonPath("data.securityEventId").isEmpty())
                .andExpect(jsonPath("data.leaseToken").doesNotExist());
        mvc.perform(multipart(URL).file(part()).header("X-Device-Api-Key", "device-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("code").value("STREAMING_VIDEO_FOUND"));
        when(videos.find("session")).thenReturn(view());
        mvc.perform(get(URL).header("X-Device-Api-Key", "device-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.storagePath").value("s3://bucket/stored.mp4"));
    }
    @Test void requiresDeviceKeyForReadAndUpload() throws Exception {
        mvc.perform(multipart(URL).file(part())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(URL).file(part()).header("X-Vision-Callback-Key", "vision-key")).andExpect(status().isUnauthorized());
        mvc.perform(multipart(URL).file(part()).header("Authorization", "Bearer user-token")).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).header("X-Device-Api-Key", "wrong")).andExpect(status().isUnauthorized());
        verifyNoInteractions(videos);
    }
    @Test void rejectsMissingEmptyOversizeAndWrongTransport() throws Exception {
        mvc.perform(multipart(URL).header("X-Device-Api-Key", "device-key")).andExpect(status().isBadRequest());
        mvc.perform(multipart(URL).file(new MockMultipartFile("file", "empty.mp4", "video/mp4", new byte[0]))
                .header("X-Device-Api-Key", "device-key")).andExpect(status().isBadRequest());
        var huge = new MockMultipartFile("file", "huge.mp4", "video/mp4", mp4(1)) {
            @Override public long getSize() { return StreamingVideoFileInfo.MAX_BYTES + 1; }
        };
        mvc.perform(multipart(URL).file(huge).header("X-Device-Api-Key", "device-key")).andExpect(status().is(413));
        mvc.perform(post(URL).contentType("application/json").content("{}").header("X-Device-Api-Key", "device-key"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(videos);
    }
    @Test void mapsSessionConflictStorageAndDatabaseErrors() throws Exception {
        var errors = java.util.List.of(VISION_SESSION_NOT_FOUND, STREAMING_VIDEO_CONFLICT, INVALID_STREAMING_MP4,
                STREAMING_VIDEO_STORAGE_FAILED, STREAMING_VIDEO_DATABASE_FAILED);
        for (var error : errors) {
            doThrow(new BusinessException(error)).when(videos).upload(anyString(), any());
            mvc.perform(multipart(URL).file(part()).header("X-Device-Api-Key", "device-key"))
                    .andExpect(status().is(error.getStatus().value())).andExpect(jsonPath("code").value(error.getCode()));
        }
        when(videos.find("session")).thenThrow(new BusinessException(STREAMING_VIDEO_NOT_FOUND));
        mvc.perform(get(URL).header("X-Device-Api-Key", "device-key")).andExpect(status().isNotFound());
    }
    @Test void fileBackedRequestIsCleanedAfterSuccessAndFailure() throws Exception {
        var captured = new java.util.ArrayList<MediaFile>();
        when(videos.upload(anyString(), any())).thenAnswer(call -> {
            MediaFile file = call.getArgument(1); assertArrayEquals(mp4(1), file.bytes()); captured.add(file);
            if (captured.size() == 2) throw new BusinessException(STREAMING_VIDEO_STORAGE_FAILED);
            return new StreamingSessionVideoInputPort.Receipt(view(), true);
        });
        mvc.perform(multipart(URL).file(part()).header("X-Device-Api-Key", "device-key")).andExpect(status().isCreated());
        mvc.perform(multipart(URL).file(part()).header("X-Device-Api-Key", "device-key")).andExpect(status().isBadGateway());
        for (var file : captured) assertThrows(IOException.class, file::openStream);
    }
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, StreamingSessionVideoController.class, StreamingVideoTransportErrorHandler.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean JwtProvider jwt() { return mock(JwtProvider.class); }
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean ErrorResponseWriter writer() { return new ErrorResponseWriter(new ObjectMapper()); }
        @Bean StreamingSessionVideoInputPort videos() { return mock(StreamingSessionVideoInputPort.class); }
    }
}
