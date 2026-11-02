package com.omniguardy.backend.domain.visionsession.domain.model;

import java.time.OffsetDateTime;

/** Only a revalidated, committed V8 VALID row may be projected into this input. */
public record StreamingAgentSource(VisionBinding binding, OffsetDateTime triggeredAt, String rawResultJson) {}
