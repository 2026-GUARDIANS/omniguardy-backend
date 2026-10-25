package com.omniguardy.backend.domain.detection.presentation;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveAudioInputPort;
import com.omniguardy.backend.domain.detection.presentation.mapper.DetectionPresentationMapper;
import com.omniguardy.backend.global.error.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EdgeAudioControllerTest {
    @Test void legacyMultipartContractIsPreserved() throws Exception {
        var port = mock(ReceiveAudioInputPort.class);
        when(port.receive(any())).thenReturn(new AudioReceipt("id", "id_chunk.wav", 3, AudioEventType.KNOCK, 0.98));
        var mvc = MockMvcBuilders.standaloneSetup(new EdgeAudioController(port, new DetectionPresentationMapper()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(multipart("/api/edge/audio").file(new MockMultipartFile(
                        "file", "chunk.wav", "audio/wav", new byte[]{1, 2, 3})))
                .andExpect(status().isOk()).andExpect(jsonPath("data.eventId").value("id"))
                .andExpect(jsonPath("data.filename").value("id_chunk.wav"))
                .andExpect(jsonPath("data.size").value(3));
        verify(port).receive(argThat(file -> file.originalFilename().equals("chunk.wav")
                && file.size() == 3 && file.contentType().equals("audio/wav")));
        mvc.perform(multipart("/api/edge/audio")).andExpect(status().isBadRequest());
    }
}
