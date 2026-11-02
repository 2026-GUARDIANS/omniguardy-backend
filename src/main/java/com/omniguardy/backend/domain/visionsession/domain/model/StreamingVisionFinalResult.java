package com.omniguardy.backend.domain.visionsession.domain.model;

/** JSON fragments preserve heterogeneous events and early-event references without a legacy DTO dependency. */
public record StreamingVisionFinalResult(VisionBinding binding, VisionAnalysisState analysisState,
        VisionAnalysisCompleteness analysisCompleteness, String status, Integer trackedPersonCount,
        String visionEventsJson, String relatedEarlyEventInstanceIdsJson, String streamingPocScope,
        String error, String emittedAt, String rawJson) {

    public VisionFinalOutcome evaluate(VisionBinding expected) {
        if (!expected.equals(binding)) return VisionFinalOutcome.REJECTED;
        if (analysisState == VisionAnalysisState.FAILED || "failed".equals(status)) return VisionFinalOutcome.FAILED;
        if ("partial".equals(status) || analysisCompleteness == VisionAnalysisCompleteness.PARTIAL_SAMPLED_TIME_AXIS)
            return VisionFinalOutcome.PARTIAL;
        var required = expected.triggerType() == VisionTriggerType.KEYPAD
                ? VisionAnalysisCompleteness.COMPLETE_KEYPAD : VisionAnalysisCompleteness.COMPLETE_AUDIO;
        return analysisState == VisionAnalysisState.COMPLETED && "success".equals(status)
                && analysisCompleteness == required && error == null
                ? VisionFinalOutcome.VALID : VisionFinalOutcome.REJECTED;
    }
}
