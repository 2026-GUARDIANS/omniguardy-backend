# Spring 처리 시간 JSON 로그

현재 경로는 **Pi audio_monitor.py → Audio AI 직접 호출 → Spring /api/internal/ai/audio-result**다. 이 경로의 Spring 계측에는 Audio AI 추론 시간이 없다. 기존 오디오 파일 업로드 API가 호출할 때만 `LEGACY_AUDIO_AI_HTTP`가 발생한다. 이번 변경은 관측 기능이며 운영 배포·운영 DB 수정·실제 외부 서비스 호출을 수행하지 않는다.

## 측정 구간

각 행은 별도의 시작/종료 구간이다. 중첩 구간을 합산하면 중복 계산된다.

| 요청 항목 | stage | 시작 → 종료 |
|---|---|---|
| 1. Audio 결과 HTTP 수신 | AUDIO_RESULT_HTTP | Servlet Filter 진입 → 인증/역직렬화/검증/Controller 처리 후 HTTP 응답 반환 |
| 1. Audio 결과 처리 | AUDIO_RESULT_PROCESSING | 입력 검증 → 중복/쿨다운 처리, 이벤트 처리, CAMERA_START 요청, 서비스 트랜잭션 반환 |
| 1. SecurityEvent 처리 | AUDIO_SECURITY_EVENT | 엔티티 구성 → 기존 repository.save 반환. 커밋은 상위 AUDIO_RESULT_PROCESSING에 포함 |
| 2. CAMERA_START | MQTT_CAMERA_START | 명령 직렬화 → 기존 비동기 MQTT gateway 반환 |
| 3. Session 등록 | VISION_SESSION_HTTP, VISION_SESSION_REGISTER | HTTP 수신/응답, 세션 검증/INSERT 커밋 또는 동일 등록 재시도 반환 |
| 4. EARLY_EVENT | EARLY_EVENT_HTTP, EARLY_EVENT_STORE | HTTP 수신/응답, 바인딩 검증/INSERT 커밋 또는 동일 콜백 재시도 반환 |
| 5. Polling HTTP | VISION_STATUS_HTTP, VISION_RESULT_HTTP | 기존 GET 요청 → 응답 본문 수신 및 계약 파싱. 202 pending도 기록 가능 |
| 6. 최종 결과 저장 | VISION_FINAL_STORE | 기존 최종 저장 트랜잭션 내부 진입 → COMMITTED/ROLLED_BACK. stale lease는 STALE_IGNORED |
| 7. Agent | AGENT_EXECUTION | 기존 Agent UseCase 진입 → 조회/판단/저장/기존 대응 처리 반환 |
| 7. 판단 구간 | AGENT_RISK_ASSESSMENT | 기존 컨텍스트/판단 기준 계산 → AI 호출, 결과 매핑/저장 반환 |
| 7. 실제 LLM 호출 | AGENT_LLM_CALL | 기존 LLM API 호출 직전 → 응답/계약 파싱 반환. SDK 내부 재시도도 해당 구간에 포함 |
| 8. MP4 fallback | FALLBACK_MP4_VISION | 원본 다운로드 후 실제 VisionAnalysisPort 호출 → 기존 바인딩/결과 검증. 캐시 재사용 시 발생하지 않음 |
| 8. 공통 MP4 HTTP | MP4_VISION_HTTP | 기존 LEGACY/fallback MP4 HTTP 어댑터 진입 → 분석 결과 반환/예외 |
| 9. FCM 처리 | FCM_DISPATCH | 알림 저장/활성 토큰 조회/기존 전송 루프 → 트랜잭션 완료. 토큰이 없으면 NO_ACTIVE_TOKENS |
| 9. FCM 요청 | FCM_SEND_REQUEST | 토큰별 기존 Firebase 요청 구성 → FirebaseMessaging.send 반환/예외 |
| 10. WARNING/SIREN | MQTT_SOUND_COMMAND | 기존 경고음 명령 직렬화 → MQTT gateway 반환. resultCode로 WARNING/SIREN 구분 |
| 11. 이벤트 이후 경과 | EVENT_TO_FCM_REQUEST, EVENT_TO_SOUND_COMMAND | 기존 DB의 서버 트리거 시각 → 해당 전송 요청 반환/실패 시각 |

`AUDIO_RESULT_HTTP` 등의 수신 시작은 애플리케이션 Servlet 진입이다. Pi의 전송 시작, EC2 소켓 첫 바이트 도착, 프록시 대기 시간을 직접 측정하지 않는다. Filter는 인증 이전에 실행하므로 인증 실패·잘못된 JSON도 HTTP 상태로 기록하며, 해석할 수 없는 요청의 식별자는 NULL이다. 처리되지 않은 예외로 HTTP 상태가 아직 확정되지 않았다면 httpStatus를 추측해 채우지 않는다.

HTTP 200은 HTTP 요청 성공을 뜻한다. 예를 들어 Vision status가 FAILED인 200 응답은 `status=SUCCESS, httpStatus=200, resultCode=FAILED`다. 마찬가지로 `VISION_FINAL_STORE`의 `status=SUCCESS, resultCode=TIMEOUT`은 타임아웃 상태의 **저장 성공**이다. 결과와 저장 성공을 구분한다.

## 시간과 상관관계

- 각 동기 구간의 durationMs는 `System.nanoTime()` 차이다. startedAt/completedAt/timestamp는 `Instant.now()`의 UTC ISO 8601이다. 벽시계 보정 때문에 이 두 시각의 차이가 durationMs와 정확히 같지 않을 수 있다.
- Worker 경과 시간에는 DB에 이미 저장된 서버 시각을 사용한다. AUDIO는 서버에서 생성한 SecurityEvent.triggered_at, KEYPAD는 keypad_camera_triggers.triggered_at을 읽는다. KEYPAD 이벤트 자체의 클라이언트 제공 triggeredAt, capturedAt, emittedAt을 서버 수신 시각으로 간주하지 않는다.
- 서버 트리거 기록 시각은 HTTP 수신 후 생성된다. 따라서 `EVENT_TO_*`는 `baseline=SERVER_AUDIO_TRIGGER_RECORDED` 또는 `SERVER_KEYPAD_TRIGGER_RECORDED`, `timingBasis=PERSISTED_WALL_CLOCK`를 명시하며 HTTP 전체 수신 구간과 동일하지 않다. 기존 스키마에 HTTP 최초 수신 시각을 새로 저장하지 않는다.
- **정확한 Servlet 수신부터 대응 요청 반환까지**는 AUDIO_RESULT_HTTP.startedAt과 동일 eventId의 FCM_SEND_REQUEST/MQTT_SOUND_COMMAND.completedAt을 로그에서 결합해 계산한다. KEYPAD는 KEYPAD_TRIGGER_HTTP.startedAt과 동일 triggerId로 결합한다. 여러 응답 요청이 있으면 각각 계산하고 마지막 요청까지의 최대값도 구할 수 있다. 이 방식은 로그 보존이 필요하다.
- 저장된 기준 시각이 없는 LEGACY 기록이나 관측 조회 실패는 durationMs=NULL, baseline=UNAVAILABLE로 표시한다. 대신 임의의 현재 시각이나 클라이언트 시각을 대입하지 않는다. 음수 경과 시간은 clockAnomaly=true로 표시한다.
- eventId와 sessionId는 서로 다른 식별자다. Worker의 기존 binding과 이벤트를 통해 eventId/triggerId/sessionId/triggerType를 전달한다. 별도 요청의 nanoTime 또는 다른 프로세스의 nanoTime을 빼지 않는다.
- Pi와의 연결에는 detectionId, triggerId, sessionId, eventId를 사용한다. requestId는 Spring이 요청마다 생성한 로컬 식별자다. Pi/EC2 절대시각 비교는 양쪽 시계 동기화 상태를 확인해야 한다.
- attempt/retryCount는 알고 있는 Worker 작업 시도 번호다. Polling은 polling attempts, fallback Vision은 visionAttempts+1, Streaming Agent는 preparation attempts다. SDK 내부 HTTP 재시도 횟수는 알 수 없어 추측하지 않는다. 동기/수동 호출의 attempt는 NULL일 수 있다.

## 발행과 실제 수신의 구분

현재 MQTT handler는 기존 그대로 async=true다. `CLIENT_HANDOFF_ACCEPTED` 및 `observationPoint=MQTT_CLIENT_HANDOFF`는 gateway가 반환했다는 뜻이다. 브로커 PUBACK, Pi 수신, 사이렌 재생 완료를 뜻하지 않는다. gateway까지 전달된 동기 실패/타임아웃은 기록하지만, 반환 이후의 비동기 전송 실패는 이 요청 구간의 성공을 취소하지 않는다. 실제 수신/재생 확인은 Pi 로그와 별도의 ACK가 필요하다.

FCM은 `observationPoint=FCM_PROVIDER_API_RETURN`이며 Firebase SDK 요청 반환을 측정한다. 휴대폰 도착/표시 시각이 아니다. 두 종류 모두 `recipientDeliveryConfirmed=false`다. 기존 FCM/경고음 발행 조건과 best-effort 예외 처리, Worker 재시도·멱등성은 유지한다. 기존 로직상 발행하지 않은 경우 전송 성공 로그를 만들지 않는다.

## JSON 예시

아래 ID/시각/소요 시간은 설명용이다. 실제 콘솔에서는 줄 접두사 없는 JSON 한 줄로 출력된다.

```json
{"schemaVersion":1,"timestamp":"2026-11-04T05:00:00.024Z","source":"SPRING","stage":"AUDIO_RESULT_PROCESSING","startedAt":"2026-11-04T05:00:00.001Z","completedAt":"2026-11-04T05:00:00.024Z","eventId":"evt-demo","triggerId":"trg-demo","sessionId":null,"triggerType":"AUDIO","detectionId":"pi-detection-demo","requestId":"request-demo","durationMs":23.1,"status":"SUCCESS","attempt":null,"retryCount":null,"timingBasis":"MONOTONIC","resultCode":"TRIGGERED"}
{"schemaVersion":1,"timestamp":"2026-11-04T05:00:10.200Z","source":"SPRING","stage":"MQTT_SOUND_COMMAND","startedAt":"2026-11-04T05:00:10.199Z","completedAt":"2026-11-04T05:00:10.200Z","eventId":"evt-demo","triggerId":"trg-demo","sessionId":"session-demo","triggerType":"KEYPAD","detectionId":null,"requestId":null,"durationMs":0.7,"status":"SUCCESS","attempt":1,"retryCount":0,"timingBasis":"MONOTONIC","resultCode":"WARNING_CLIENT_HANDOFF_ACCEPTED","observationPoint":"MQTT_CLIENT_HANDOFF","recipientDeliveryConfirmed":false}
{"schemaVersion":1,"timestamp":"2026-11-04T05:00:10.200Z","source":"SPRING","stage":"EVENT_TO_SOUND_COMMAND","startedAt":"2026-11-04T05:00:00Z","completedAt":"2026-11-04T05:00:10.200Z","eventId":"evt-demo","triggerId":"trg-demo","sessionId":"session-demo","triggerType":"KEYPAD","detectionId":null,"requestId":null,"durationMs":10200.0,"status":"SUCCESS","attempt":1,"retryCount":0,"timingBasis":"PERSISTED_WALL_CLOCK","baseline":"SERVER_KEYPAD_TRIGGER_RECORDED","clockAnomaly":false,"observationPoint":"MQTT_CLIENT_HANDOFF","recipientDeliveryConfirmed":false}
```

실패는 status=FAILURE/TIMEOUT, errorType, 가능한 httpStatus/resultCode를 사용한다. 예외 메시지·스택·응답 본문·토큰·JWT·API Key·FCM 본문·사용자 정보·파일/서비스 URL은 성능 로그에 쓰지 않는다. 일반 애플리케이션 로그의 기존 예외 로깅 정책은 변경하지 않는다.

## 설정과 로그량

| 설정 | 기본값 | 설명 |
|---|---|---|
| PERFORMANCE_LOG_ENABLED | true | JSON 성능 로그 활성화 |
| PERFORMANCE_POLL_SAMPLE_EVERY | 5 | 정상 pending Polling은 첫 번째와 매 N번째 시도를 출력 |

최종 상태/결과와 실패/타임아웃은 샘플링하지 않는다. sampleEvery=1이면 모든 Polling 요청을 기록한다. 각 구간은 시작/종료 두 줄 대신 완료 시점 한 줄에 양쪽 시각을 담는다. 로그인·헬스체크·조회 API 전체에 무차별 HTTP 로그를 추가하지 않는다.

기존 AgentPerformanceMetrics의 횟수/평균/p95/실패/타임아웃 집계는 그대로 재사용한다. 중복되는 기존 텍스트 성능 로그만 DEBUG로 이동했다. 기존 집계 텍스트가 필요하면 해당 클래스의 로거만 DEBUG로 설정할 수 있다. 로깅 설정과 성능 설정은 Worker 스케줄·재시도 조건을 변경하지 않는다.

## 운영 로그 확인 방법

다음은 향후 운영자가 실행할 **조회 예시**다. 이번 작업에서는 EC2 접속이나 아래 운영 명령을 실행하지 않았다. 배포된 컨테이너에 새 코드가 포함되어야 새 로그가 나타난다. 기존 Compose의 env_file로 설정 환경 변수를 전달할 수 있다.

```bash
# 저장소 루트에서 최근 10분의 JSON 성능 로그만 추출
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml logs --no-color --no-log-prefix --since 10m backend \
  | jq -Rc 'fromjson? | select(.source == "SPRING")' > spring-performance.jsonl

# 이벤트별 처리 단계
jq -c --arg id 'evt-demo' 'select(.eventId == $id) | {timestamp,stage,eventId,triggerId,sessionId,durationMs,status,httpStatus,resultCode,attempt}' spring-performance.jsonl

# HTTP 또는 Worker의 타임아웃 관측
jq -c 'select(.status == "TIMEOUT" or .resultCode == "TIMEOUT")' spring-performance.jsonl
```

`--timestamps` 옵션을 붙이면 JSON 앞에 Docker 타임스탬프가 추가되므로 JSON 직접 파싱에는 사용하지 않는다. 기존 Docker 로그 순환 설정(10MB × 3)은 유지한다. 긴 통합 테스트를 분석할 때는 필요한 구간을 먼저 파일로 보존한다.

## 수정 파일

Java 경로는 `src/main/java/com/omniguardy/backend/` 기준이다.

| 위치 | 파일 |
|---|---|
| global/performance (신규) | PerformanceLog, PerformanceHttpFilter, PerformanceLogConfiguration |
| domain/detection/application | AudioDetectionService, ReceiveAudioEventUseCase, TriggerKeypadCameraService |
| domain/detection/infrastructure | MqttCameraCommandAdapter, AudioFastApiAdapter, VisionFastApiAdapter |
| domain/detection/presentation | AudioEventController |
| domain/visionsession/application/usecase | VisionSessionService, ReceiveVisionEarlyEventService, PollStreamingVisionService, ProcessStreamingAgentService, ProcessVisionFallbackService |
| domain/visionsession/infrastructure | StreamingVisionHttpAdapter, JdbcVisionPollingRepository |
| domain/securityevent/application | AnalyzeAgentRiskUseCase, RiskResponseService, AgentPerformanceMetrics; 신규 ServerEventTimingPort |
| domain/securityevent/infrastructure | LangChainRiskAssessmentAdapter; 신규 JdbcServerEventTimingAdapter |
| domain/notification | NotificationService, FirebasePushAdapter |
| domain/sound/infrastructure | MqttSoundCommandAdapter |
| 설정 | src/main/resources/logback-spring.xml (신규), application.yml, .env.example, deploy/.env.prod.example |

읽기 전용 관측 조회 Port/Adapter는 기존 테이블만 SELECT하며 Agent당 최대 한 번 조회한다. 조회 실패가 Agent/대응 결과를 바꾸지 않는다. 새 테이블/컬럼/Flyway migration은 없다. DDD domain model에 로거를 넣지 않았으며, 다른 도메인 테이블을 읽는 관측 SQL은 Infrastructure Adapter에 있다.

## 검증

- PerformanceLogTest: UTC JSON, 단조 시간, 상관관계 격리, 실패 원본 보존, 중첩 타임아웃, 민감정보 제외, Polling 샘플링, 관측 비활성화, 트랜잭션 커밋/롤백 후 기록, HTTP 인증 실패/예외.
- PerformanceFlowTest: Pi 분석 결과 처리와 CAMERA_START(오디오 추론/파일 저장 없음), WARNING/SIREN 발행 횟수·기존 payload, Firebase SDK Mock 성공/타임아웃, 실제 수신과의 구분.
- JdbcServerEventTimingAdapterTest: 저장된 UTC 서버 시각, KEYPAD 클라이언트 시각 배제, 기준 시각이 없는 LEGACY 기록.
- 기존 StreamingAgentIntegrationTest/VisionFallbackIntegrationTest: 실제 Worker에서 sessionId/eventId/triggerId/attempt 전파와 AI/대응 호출 횟수 보존.
- 기존 VisionPollingPersistenceTest: DB 오류 롤백 후 실패 로그, 재시도 커밋 후 성공 로그. 기존 StreamingVisionHttpAdapterTest: 로컬 HTTP fixture의 read timeout과 connection failure 분류.
- 기존 Agent 메트릭·판단 기준·LEGACY·Streaming V6~V11·FCM/MQTT·업로드·fallback 전체 회귀 테스트.

외부 MySQL/MQTT 테스트 환경 변수는 해제하고 H2·Mock 및 루프백 HTTP fixture로 실행한다. AWS/S3·실제 Firebase/OpenAI/Vision/Audio AI·EC2에 호출하지 않는다.

### 실행 결과 (2026-11-04)

- `./gradlew.bat build`: BUILD SUCCESSFUL, 59초.
- 총 409개 중 398개 통과, 실패/오류 0개, 11개 건너뛰기. 건너뛴 항목은 외부 MySQL 전용 ProdDatabaseIntegrationTest 9개, MQTT 접속 환경 변수 전용 테스트 1개, 기존 비활성 smoke 테스트 1개다.
- 기존 Agent 메트릭 집계와 DEBUG 텍스트 로그 검증도 통과했다. JSON 성공·실패·타임아웃, MQTT 클라이언트/쓰기 타임아웃 분류, FCM SDK Mock, Worker 상관관계, 최종 저장 커밋/롤백을 검증했다.
- 콘솔의 접두사 없는 SPRING JSON 로그를 테스트 출력에서 직접 파싱해 확인했다.
- `git diff --check` 통과. 새 의존성이나 migration은 추가하지 않았다.
- HTML 테스트 보고서: `build/reports/tests/test/index.html`.
