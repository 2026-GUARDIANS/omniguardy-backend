package com.omniguardy.backend.domain.securityevent.domain.model;

import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Getter
@Table(name = "security_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SecurityEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ?섎굹??Audio ??Video ??Vision ??Agent ?ш굔??臾띕뒗 ID
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "trigger_id", length = 64)
    private String triggerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 20)
    private EventTriggerType triggerType;

    @Column(name = "triggered_at")
    private OffsetDateTime triggeredAt;

    // ?대떦 蹂댁븞 ?대깽?몄쓽 ?ъ슜??
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;


    // =========================
    // Audio AI 寃곌낵
    // =========================

    @Enumerated(EnumType.STRING)
    @Column(name = "audio_status", length = 20)
    private AudioEventType audioEventType;

    @Column(name = "audio_probability")
    private Double audioProbability;

    // ?꾩옱 濡쒖뺄 媛쒕컻??
    @Column(name = "audio_path", length = 500)
    private String audioPath;


    // =========================
    // Video
    // =========================

    // ?꾩옱??濡쒖뺄 寃쎈줈
    // 諛고룷 ??S3 key ?먮뒗 URL 援ъ“濡?蹂寃?
    @Column(name = "video_path", length = 500)
    private String videoPath;


    // =========================
    // Vision AI 寃곌낵
    // =========================

    @Column(name = "vision_prediction", length = 20)
    private String visionPrediction;

    @Column(name = "vision_confidence")
    private Double visionConfidence;

    @Column(name = "vision_analysis_mode", length = 40)
    private String visionAnalysisMode;

    @Lob
    @Column(name = "class_probabilities", columnDefinition = "TEXT")
    private String classProbabilities;

    @Lob
    @Column(name = "vision_events", columnDefinition = "TEXT")
    private String visionEvents;

    @Column(name = "person_count")
    private Integer personCount;

    @Column(name = "video_duration_seconds")
    private Double videoDurationSeconds;

    @Column(name = "vision_analyzed_at")
    private OffsetDateTime visionAnalyzedAt;


    // =========================
    // Agent AI 寃곌낵
    // =========================

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "agent_reason", length = 1000)
    private String agentReason;


    // ?꾩옱 泥섎━ ?④퀎
    // AUDIO_DETECTED / CAMERA_REQUESTED /
    // VISION_ANALYZED / COMPLETED / FAILED
    @Column(name = "status", nullable = false, length = 30)
    private String status;


    public void updateStatus(String status) {
        this.status = status;
    }

    public void markCameraRequested() {
        if (!SecurityEventStatus.AUDIO_DETECTED.name().equals(status)) {
            throw new BusinessException(SecurityEventErrorCode.INVALID_EVENT_STATUS);
        }
        this.status = SecurityEventStatus.CAMERA_REQUESTED.name();
    }

    public void updateVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public void connectAudioTrigger(String triggerId, OffsetDateTime triggeredAt) {
        if (this.triggerId == null) this.triggerId = triggerId;
        if (this.triggerType == null) this.triggerType = EventTriggerType.AUDIO;
        if (this.triggeredAt == null) this.triggeredAt = triggeredAt;
    }

    public void updateVisionResult(
            String prediction,
            Double confidence,
            String analysisMode,
            String classProbabilities,
            String visionEvents,
            Integer personCount,
            Double videoDurationSeconds,
            OffsetDateTime analyzedAt
    ) {
        this.visionPrediction = prediction;
        this.visionConfidence = confidence;
        this.visionAnalysisMode = analysisMode;
        this.classProbabilities = classProbabilities;
        this.visionEvents = visionEvents;
        this.personCount = personCount;
        this.videoDurationSeconds = videoDurationSeconds;
        this.visionAnalyzedAt = analyzedAt;
        this.status = SecurityEventStatus.VISION_ANALYZED.name();
    }

    public void updateVisionResult(
            String prediction,
            Double confidence,
            String classProbabilities,
            String visionEvents,
            Integer personCount,
            Double videoDurationSeconds,
            OffsetDateTime analyzedAt
    ) {
        updateVisionResult(prediction, confidence, null, classProbabilities, visionEvents,
                personCount, videoDurationSeconds, analyzedAt);
    }

    public void updateAgentResult(
            String riskLevel,
            int riskScore,
            String agentReason
    ) {
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.agentReason = agentReason;
        this.status = SecurityEventStatus.COMPLETED.name();
    }

    public void completeWithoutRisk() {
        this.riskLevel = null;
        this.riskScore = null;
        this.agentReason = null;
        this.status = SecurityEventStatus.COMPLETED.name();
    }

    public boolean isVisionAnalyzed() {
        return SecurityEventStatus.VISION_ANALYZED.name().equals(this.status);
    }

    public void fail() {
        this.status = SecurityEventStatus.FAILED.name();
    }
}


