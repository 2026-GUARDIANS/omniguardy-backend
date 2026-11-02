package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.visionsession.application.model.StreamingVideoFileInfo;
import com.omniguardy.backend.domain.visionsession.infrastructure.video.Mp4VideoValidator;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static com.omniguardy.backend.domain.visionsession.StreamingMp4TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamingMp4ValidationTest {
    Mp4VideoValidator validator = new Mp4VideoValidator();
    @Test void validatesContainerVideoTrackAndHashWithoutChangingBytes() {
        var file = file(1); var before = file.bytes(); var info = validator.validate(file);
        assertEquals(file.sha256(), info.sha256()); assertEquals(file.size(), info.sizeBytes()); assertArrayEquals(before, file.bytes());
    }
    @Test void extensionOrMimeSpoofingAndNonMp4AreRejected() {
        for (MediaFile file : new MediaFile[]{new MediaFile("v.mov", "video/mp4", mp4(1)),
                new MediaFile("v.mp4", "image/jpeg", mp4(1)), new MediaFile("v.mp4", "application/octet-stream", mp4(1)),
                new MediaFile("v.mp4", "video/mp4", "not mp4".getBytes())}) {
            assertCode("INVALID_STREAMING_MP4", file);
        }
    }
    @Test void incompleteOversizedBoxesAndAudioOnlyMp4AreRejected() {
        byte[] valid = mp4(1); byte[] oversizedBox = valid.clone(); java.nio.ByteBuffer.wrap(oversizedBox).putInt(Integer.MAX_VALUE);
        byte[] audio = valid.clone();
        for (int i = 0; i < audio.length - 3; i++) if (audio[i] == 'v' && audio[i+1] == 'i' && audio[i+2] == 'd' && audio[i+3] == 'e') {
            System.arraycopy("soun".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, audio, i, 4); break;
        }
        for (byte[] bytes : new byte[][]{Arrays.copyOf(valid, valid.length - 1), oversizedBox, audio,
                box("ftyp", new byte[12]), concat(valid, new byte[]{0})})
            assertCode("INVALID_STREAMING_MP4", new MediaFile("v.mp4", "video/mp4", bytes));
    }
    @Test void emptyAndOverLimitFilesAreRejectedBeforeOpeningStreams() {
        assertCode("EMPTY_STREAMING_VIDEO", new MediaFile("v.mp4", "video/mp4", new byte[0]));
        MediaFile huge = mock(MediaFile.class); when(huge.size()).thenReturn(StreamingVideoFileInfo.MAX_BYTES + 1);
        assertCode("STREAMING_VIDEO_TOO_LARGE", huge); verifyNoMoreInteractions(ignoreStubs(huge));
    }
    @Test void safeOriginalNameIsMetadataOnly() {
        var info = validator.validate(new MediaFile("../../folder\\cam.MP4", "VIDEO/MP4", mp4(1)));
        assertEquals("cam.MP4", info.originalFilename());
    }
    private void assertCode(String code, MediaFile file) {
        assertEquals(code, assertThrows(BusinessException.class, () -> validator.validate(file)).getErrorCode().getCode());
    }
}
