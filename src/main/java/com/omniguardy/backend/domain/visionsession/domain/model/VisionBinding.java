package com.omniguardy.backend.domain.visionsession.domain.model;

/** Expected IDs are snapshotted from the registered session and issued trigger, never from Vision. */
public record VisionBinding(String sessionId, VisionTriggerType triggerType, String triggerId, String securityEventId) {}
