package com.omniguardy.backend.domain.visionsession.presentation.dto;

import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/** Strict JSON types at this boundary only; never change legacy Jackson coercion settings. */
public record VisionEarlyEventRequest(
        @Schema(allowableValues = "EARLY_EVENT", requiredMode = Schema.RequiredMode.REQUIRED) String messageType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128) String sessionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 64) String triggerId,
        @Schema(nullable = true, maxLength = 36) String securityEventId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VisionTriggerType triggerType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 256) String eventInstanceId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long sequence,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "true") Boolean provisional,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Event event,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 64) String emittedAt) {
    public record Event(@Schema(allowableValues = "SILENT_SIGNAL", requiredMode = Schema.RequiredMode.REQUIRED) String eventType,
                        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long detectedFrame,
                        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long confirmedFrame,
                        @Schema(nullable = true) String capturedAt) {}

    public static VisionEarlyEventRequest from(JsonNode root) {
        if (root == null || !root.isObject()) throw invalid();
        var event = root.get("event");
        if (event == null || !event.isObject()) throw invalid();
        var provisional = root.get("provisional");
        if (provisional == null || !provisional.isBoolean()) throw invalid();
        VisionTriggerType triggerType;
        try { triggerType = VisionTriggerType.valueOf(string(root, "triggerType", false)); }
        catch (IllegalArgumentException exception) { throw invalid(); }
        return new VisionEarlyEventRequest(string(root, "messageType", false), string(root, "sessionId", false),
                string(root, "triggerId", false), string(root, "securityEventId", true), triggerType,
                string(root, "eventInstanceId", false), integer(root, "sequence"), provisional.booleanValue(),
                new Event(string(event, "eventType", false), integer(event, "detectedFrame"),
                        integer(event, "confirmedFrame"), string(event, "capturedAt", true)),
                string(root, "emittedAt", false));
    }

    public VisionEarlyEvent toDomain() {
        if (!"EARLY_EVENT".equals(messageType) || !Boolean.TRUE.equals(provisional)
                || !"SILENT_SIGNAL".equals(event.eventType())) throw invalid();
        return new VisionEarlyEvent(sessionId, triggerId, securityEventId, triggerType, eventInstanceId, sequence,
                event.detectedFrame(), event.confirmedFrame(), event.capturedAt(), emittedAt);
    }

    private static String string(JsonNode object, String name, boolean nullable) {
        var value = object.get(name);
        if (value == null || value.isNull()) {
            if (nullable) return null;
            throw invalid();
        }
        if (!value.isString()) throw invalid();
        return value.stringValue();
    }

    private static long integer(JsonNode object, String name) {
        var value = object.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw invalid();
        return value.longValue();
    }

    private static BusinessException invalid() { return new BusinessException(GlobalErrorCode.INVALID_REQUEST); }
}
