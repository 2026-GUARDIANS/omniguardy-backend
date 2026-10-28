package com.omniguardy.backend.domain.detection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audio_detection_receipts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AudioDetectionReceipt {
    @Id
    @Column(name = "detection_id", length = 128, nullable = false)
    private String detectionId;

    public AudioDetectionReceipt(String detectionId) { this.detectionId = detectionId; }
}
