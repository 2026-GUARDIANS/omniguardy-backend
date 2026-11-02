package com.omniguardy.backend.domain.visionsession.application.model;
import com.omniguardy.backend.domain.detection.application.model.VideoTriggerContext;
import com.omniguardy.backend.domain.visionsession.domain.model.StreamingSessionVideo;
import java.util.Optional;
public record VisionFallbackSource(VideoTriggerContext context, Optional<StreamingSessionVideo> video) {}

