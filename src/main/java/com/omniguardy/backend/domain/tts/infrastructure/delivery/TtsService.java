package com.omniguardy.backend.domain.tts.infrastructure.delivery;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import com.omniguardy.backend.domain.tts.infrastructure.mqtt.dto.TtsAudioMessage;
import com.omniguardy.backend.domain.tts.application.port.out.TtsDeliveryPort;
import com.omniguardy.backend.domain.tts.domain.error.TtsErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import com.omniguardy.backend.global.mqtt.MqttConfig.MqttGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService implements TtsDeliveryPort {

    private final MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.topic.tts}")
    private String ttsTopic;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.tts.audio-dir}")
    private String audioDir;

    @Value("${app.tts.audio-url-prefix}")
    private String audioUrlPrefix;

    public void synthesizeAndPublish(String message) {
        String deliveryId = UUID.randomUUID().toString();
        String stage = "AUDIO_DIRECTORY_CREATE";
        try {
            String fileName = deliveryId + ".mp3";

            Path dirPath = Path.of(audioDir);
            Files.createDirectories(dirPath);

            Path filePath = dirPath.resolve(fileName);

            stage = "GOOGLE_TTS_SYNTHESIS";
            log.info("TTS stage started: deliveryId={}, stage={}", deliveryId, stage);
            byte[] audioBytes = synthesizeSpeech(message);
            log.info("TTS stage completed: deliveryId={}, stage={}, audioBytes={}",
                    deliveryId, stage, audioBytes.length);

            stage = "AUDIO_FILE_WRITE";
            Files.write(filePath, audioBytes);

            String audioUrl = baseUrl + audioUrlPrefix + "/" + fileName;

            stage = "MQTT_PAYLOAD_SERIALIZATION";
            TtsAudioMessage mqttMessage = new TtsAudioMessage("TTS_AUDIO", audioUrl);
            String payload = objectMapper.writeValueAsString(mqttMessage);

            stage = "MQTT_PUBLISH";
            log.info("TTS stage started: deliveryId={}, stage={}, topic={}", deliveryId, stage, ttsTopic);
            mqttGateway.sendToMqtt(payload, ttsTopic);
            log.info("TTS MQTT publish request accepted (async): deliveryId={}, stage={}, topic={}",
                    deliveryId, stage, ttsTopic);

        } catch (Exception e) {
            log.error("TTS delivery failed: deliveryId={}, stage={}, topic={}",
                    deliveryId, stage, ttsTopic, e);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(TtsErrorCode.DELIVERY_FAILED, e);
        }
    }

    private byte[] synthesizeSpeech(String text) throws Exception {
        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
            SynthesisInput input = SynthesisInput.newBuilder()
                    .setText(text)
                    .build();

            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode("ko-KR")
                    .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                    .build();

            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .build();

            SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(
                    input,
                    voice,
                    audioConfig
            );

            ByteString audioContents = response.getAudioContent();
            return audioContents.toByteArray();
        }
    }
}

