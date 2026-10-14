package com.omniguardy.backend.domain.detection.application.model;

public enum AudioEventType {
    BACKGROUND,
    KNOCK,
    HANDLE;

    public static AudioEventType fromPredictedClass(String predictedClass) {
        if (predictedClass == null) {
            throw new IllegalArgumentException("Audio predicted class must not be null");
        }
        return switch (predictedClass) {
            case "background" -> BACKGROUND;
            case "knock" -> KNOCK;
            case "handle" -> HANDLE;
            default -> throw new IllegalArgumentException("Unknown audio predicted class: " + predictedClass);
        };
    }
}
