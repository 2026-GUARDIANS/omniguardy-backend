# Audio result ingestion

## Device request

`POST https://<backend-host>/api/internal/ai/audio-result`

Headers:

```http
Content-Type: application/json
X-Device-Api-Key: <AUDIO_DEVICE_API_KEY>
```

Set `AUDIO_DEVICE_API_KEY` in the backend environment and configure the same secret
on the Raspberry Pi. An absent/blank backend secret disables access (401). User
JWTs do not authorize this endpoint. Use HTTPS when calling across the internet.
The key authorizes this endpoint only; existing user routes retain JWT security.

The Pi receives the unchanged FastAPI `/predict` response, adds `detectionId`, and
forwards it for knock/handle detections:

```json
{
  "detectionId": "c9b843f8-0171-48ef-a4b0-5c8dc844010d",
  "status": "success",
  "predicted_class": "knock",
  "probabilities": {
    "background": 0.01,
    "knock": 0.98,
    "handle": 0.01
  },
  "window_start_seconds": 2.0,
  "cooldown_suppressed": false
}
```

Required: `detectionId`, `predicted_class`, `probabilities` and its three values.
IDs accept 1–128 ASCII letters, digits, underscores or hyphens. Generate a globally
unique ID (UUID recommended) for each detection and reuse it on every retry.
All probabilities must be numbers in [0, 1]. The selected class probability is
stored without adding a confidence threshold. Other FastAPI fields are accepted
but do not govern processing; specifically `cooldown_suppressed` is ignored.
Background is accepted defensively and does not create an event or consume cooldown.

## Responses and retries

HTTP 200 uses the existing `ApiResponse` wrapper with code `AUDIO_RESULT_RECEIVED`.
Its `data` contains:

```json
{
  "detectionId": "c9b843f8-0171-48ef-a4b0-5c8dc844010d",
  "eventId": "<generated SecurityEvent UUID>",
  "outcome": "TRIGGERED"
}
```

Outcomes: `TRIGGERED`, `COOLDOWN_SUPPRESSED`, `BACKGROUND_IGNORED`, `DUPLICATE`.
Only `TRIGGERED` returns an event ID. A duplicate response acknowledges receipt;
it does not replay the original event ID or republish MQTT. Treat every 200 outcome
as completed. Invalid bodies return 400 / `INVALID_REQUEST`; missing/incorrect
device credentials return 401 / `AUTHENTICATION_REQUIRED`.

## Persistence and compatibility

Flyway `V3__create_audio_detection_receipts.sql` adds a receipt table with a
case-sensitive primary key on `detection_id`. Receipt insertion is flushed before
cooldown/event side effects, inside the event transaction. Concurrent duplicates
are rejected by the DB; duplicate handling occurs after that transaction rolls back.
Suppressed/background receipts are also committed. Failed processing rolls back
both receipt and event, permitting retry. Receipts are retained; deleting them
would permit old IDs to be processed again.

Both the legacy multipart API and the JSON API share the same in-memory 12-second
Audio cooldown. It is global to one application instance, shared by knock/handle,
and updated upon admission, including when later processing fails. It is not a
distributed cooldown. JSON requests do not store WAV data and set `audioPath` null.
Existing `omniguardy.demo.user-id` configuration and user lookup still apply.

MQTT topic/payload and 10-second camera duration are unchanged. The Pi must carry
MQTT `securityEventId` (or legacy `eventId`) into the subsequent video upload.
Vision, KEYPAD and Agent flows are unchanged.

MQTT is still requested before the event DB transaction commits. Asynchronous
publish failure may not roll back the DB; a successful publish followed by DB
failure may leave the Pi with an event ID that was not committed. DB uniqueness
does not give exactly-once MQTT delivery (existing QoS 1); the Pi should deduplicate
camera commands by `triggerId`. Outbox/after-commit delivery is outside this change.

## Later legacy removal

After Pi integration validation, remove `EdgeAudioController`, `ReceiveAudioInputPort`,
`ReceiveAudioUseCase`, `AudioReceipt`, `AudioUploadResponseDto`, and their dedicated tests.
Remove the AudioReceipt mapper overload. Remove detection's `AudioAnalysisPort`,
`AudioFastApiAdapter`, `AudioPredictResponseDto` and their tests if no longer used.
Simplify `AudioDetectionService` by removing optional file saving/filename output.
Remove `MediaStoragePort.saveAudio()` and its implementation only after checking callers;
keep video storage. Remove the old `AUDIO_RECEIVED` success code if unused.
Keep `ai.fastapi.audio.*` while `/api/ai/analyze` still uses `FastApiMediaAnalysisAdapter`.
