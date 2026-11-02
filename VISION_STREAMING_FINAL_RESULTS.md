# Streaming Vision 3단계: 촬영 종료 통지, polling, FINAL_RESULT 저장

계약은 저장소 루트의 `SPRING_BOOT_JSON_SPEC.md` (2026-11-02) 5·6절이다.
Pi가 Vision `POST /vision/complete`를 먼저 호출하고, 같은 sessionId로 백엔드에 종료를 통지한다.
백엔드는 Vision complete를 대신 호출하지 않는다. MP4 API와 Streaming PoC(8010)는 별도 클라이언트다.

## API

```http
POST /api/edge/vision/sessions/vision-test-001/complete
X-Device-Api-Key: <AUDIO_DEVICE_API_KEY>
```

요청 body는 없다. 세션 등록이 선행되어야 한다. 응답 예시:

```http
HTTP/1.1 202 Accepted
Content-Type: application/json
```

```json
{
  "success": true,
  "code": "VISION_COMPLETION_ACCEPTED",
  "message": "Vision completion notification accepted.",
  "data": {
    "sessionId": "vision-test-001",
    "processingState": "PENDING",
    "requestedAt": "2026-11-02T05:50:00Z",
    "deadlineAt": "2026-11-02T05:55:00Z",
    "attempts": 0,
    "fallbackRequired": false,
    "reason": null
  }
}
```

- 종료 통지는 DB transaction commit 후 즉시 202로 응답한다. HTTP 요청 안에서 외부 Vision을 조회하지 않는다.
- 반복 통지도 202이며 기존 작업의 현재 상태를 반환한다. deadline/시도 횟수/최초 요청 시각을 초기화하지 않는다.
  이미 성공·실패한 작업도 재시작하지 않는다.
- 기존 `X-Device-Api-Key` 필터가 그대로 보호한다. Callback 키나 사용자 JWT만으로는 호출할 수 없다.
- 400: 잘못된 sessionId. 401: 장치 키 없음/불일치. 404: 세션 또는 원래 발급된 트리거 없음.
  409: polling 작업 없이 이미 terminal 상태인 세션. 500: DB 등 내부 오류.
  오류 body는 기존 ErrorResponse를 사용한다.
- 기존 `GET /api/edge/vision/sessions/{sessionId}`의 응답 구조는 그대로다.
  처리 중에는 REGISTERED, 정상 최종 저장 후 COMPLETED, fallback 필요 시 FAILED를 반환한다.
  자세한 작업 상태는 종료 통지 재전송의 data 또는 DB에서 확인할 수 있다.

## 연결 검증

종료 통지 때 세션과 발급 기록을 읽어 기대 ID를 작업에 고정한다. Vision 응답으로 기대 ID를 만들지 않는다.

- KEYPAD: 세션 triggerId와 `keypad_camera_triggers` 발급 기록 확인. securityEventId는 null.
- AUDIO: 세션 eventId로 기존 읽기 전용 VisionAudioTriggerPort를 조회한다.
  기존 AUDIO SecurityEvent의 eventId와 발급된 triggerId를 각각 securityEventId/triggerId로 사용한다.
- status와 FINAL_RESULT 모두 sessionId/triggerId/triggerType/securityEventId를 기대 ID와 정확히 비교한다.
- 잘못된 status 연결은 즉시 실패하고 result를 조회하지 않는다.
  파싱 가능한 FINAL_RESULT 연결 불일치는 요청한 세션 아래 REJECTED 원문으로 보관하며 Agent 사용을 금지한다.
  보고된 다른 sessionId를 새로 등록하거나 그 세션을 변경하지 않는다.

## Polling 및 재시도 정책

독립된 `StreamingVisionPollScheduler`가 DB에서 실행 시각이 된 작업을 조회한다.
한 번의 작업 시도에서 status를 한 번 조회하고, terminal + finalResultAvailable이면 result를 한 번 조회한다.
수면 반복이나 HTTP 요청 스레드를 붙잡는 루프를 사용하지 않는다.

| 설정 | 기본값 | 의미 |
|---|---|---|
| STREAMING_VISION_BASE_URL | http://localhost:8010 | 별도 Streaming Vision 서버 URL |
| STREAMING_VISION_POLLING_ENABLED | true | 이 인스턴스의 자동 워커 실행 여부 |
| STREAMING_VISION_POLL_INTERVAL | 2s | 분석 진행/202/일시적 오류 후 다음 실행까지 간격 |
| STREAMING_VISION_MAX_WAIT | 5m | 최초 종료 통지부터 결과 수용 deadline |
| STREAMING_VISION_CONNECT_TIMEOUT | 2s | HTTP 연결 timeout |
| STREAMING_VISION_READ_TIMEOUT | 5s | HTTP 읽기 timeout |
| STREAMING_VISION_LEASE_DURATION | 30s | 실행 권한 점유 시간 |
| STREAMING_VISION_BATCH_SIZE | 10 | scheduler 한 회차의 최대 작업 수 (1~100) |
| STREAMING_VISION_SCHEDULER_DELAY_MS | 1000 | 회차 종료 후 다음 스캔까지 대기 |

Spring property prefix는 `ai.fastapi.streaming-vision`이다.
lease는 `2 × (connect-timeout + read-timeout) + 1s`보다 길어야 기동된다.
시간은 UTC로 저장한다. 다중 인스턴스의 시스템 시계는 동기화되어 있어야 한다.

- RECEIVING / PROCESSING: 다음 시각으로 재예약. 아직 inputComplete=false여도 대기한다.
- COMPLETED: finalResultAvailable=false면 대기, true면 result 조회. terminal인데 inputComplete=false이면 INVALID_STATUS.
- FAILED: 결과가 준비되어 있으면 failed FINAL_RESULT까지 읽어 저장한다.
  준비되지 않았으면 VISION_FAILED로 종료하고 마지막 status 원문/오류를 작업에 기록한다.
- result 202: detail body를 FINAL_RESULT로 파싱하지 않고 RESULT_PENDING으로 재예약.
- status/result 404: UPSTREAM_NOT_FOUND로 즉시 fallback 필요 상태. TTL 만료도 같은 정책이다.
- 네트워크·읽기 timeout, HTTP 408/429/5xx: 같은 작업을 고정 간격으로 재시도.
  최종 시도 횟수 제한 대신 최초 deadline으로 전체 재시도 기간을 제한한다.
- 기타 HTTP 오류·잘못된 JSON/타입·알 수 없는 enum: 영구 실패로 기록.
- deadline을 넘긴 결과는 정상 결과로 수용하지 않고 TIMEOUT 처리한다.
  재시작/중복 통지는 이 deadline을 연장하지 않는다. 스캔/대기열/진행 중 I/O 때문에
  TIMEOUT 기록 시점 자체는 deadline보다 늦을 수 있다.
- 워커가 꺼져 있으면 통지는 DB에 보존되며 활성 워커가 재개한다. 최소 한 인스턴스에서 활성화해야 한다.

한 인스턴스는 한 회차의 작업을 순차 처리한다. 설정 간격은 다음 실행 가능 시각이며,
실제 재조회 시각에는 I/O·다른 작업의 처리 시간이 더해질 수 있다.

## 재시작·중복·트랜잭션 정합성

- session_id가 작업 PK이므로 중복 종료 통지는 한 작업만 만든다. 생성 때 V6 세션 행을 잠근다.
- 실행 전 조건부 UPDATE로 PENDING 또는 만료된 RUNNING 작업을 점유한다.
  UUID lease_token을 부여하며 해당 토큰·유효한 lease가 있는 워커만 저장/재예약할 수 있다.
- 중단된 워커의 작업은 lease 만료 후 다른 워커가 다시 처리한다.
  이전 워커가 늦게 돌아와도 토큰 검증으로 상태/결과를 덮어쓸 수 없다.
  장애로 lease가 만료될 때 이미 전송한 읽기 전용 GET이 겹칠 수 있으나 DB 처리는 한 번만 commit된다.
- 네트워크 호출 동안 DB transaction/행 잠금을 유지하지 않는다.
- FINAL_RESULT insert, polling 작업 terminal 갱신, vision_sessions 상태 갱신은 한 transaction이다.
  중간 오류 시 전부 롤백되고 lease 복구로 다시 시도한다.
- FINAL_RESULT의 session_id PK가 DB 수준에서 1세션 1결과를 보장한다.
  terminal 작업은 다시 점유하지 않으며 저장된 결과를 덮어쓰지 않는다.

## V8 저장 구조

V1~V7을 수정하지 않고 `V8__create_vision_polling_and_final_results.sql`을 추가했다.
기존 vision_sessions와 vision_early_events의 스키마는 그대로다.

`vision_polling_jobs`:

- session_id PK/FK → vision_sessions, 기대 trigger_type/trigger_id/security_event_id
- processing_state: PENDING / RUNNING / SUCCEEDED / FALLBACK_REQUIRED
- requested_at, deadline_at, next_poll_at, lease_until/token, attempts, finished_at
- reason, last_error, last_status_json: 재시도/실패 사유와 마지막 유효 status 원문
- 상태·트리거 nullable·lease CHECK 제약, 실행 시각/lease 만료 검색 인덱스

`vision_final_results`:

- session_id PK/FK → vision_polling_jobs (요청한 세션)
- analysis_state, analysis_completeness, result_status, outcome
- eligible_for_agent, fallback_required: 향후 단계에서 사용할 검증 결과 플래그
- nullable tracked_person_count, vision_events_json, related_early_event_ids_json
- raw_result_json: trigger/error/streamingPocScope 등 전체 JSON과 확장 필드를 포함
- emitted_at 원문, received_at UTC
- outcome·유효 결과 플래그 CHECK 제약

JSON은 LONGTEXT로 보관하고 경계 파서에서 검증한다. 이벤트별 스키마를 고정하지 않아
빈 visionEvents와 중첩된 임의 event object를 보존한다. trackedPersonCount는 nullable Integer이며
0도 유효하다. relatedEarlyEventInstanceIds는 EARLY_EVENT 수신 순서와 관계없이 그대로 보존한다.
JSON의 공백/키 서식은 정규화되지만 필드와 값은 보존한다.

prod Flyway는 V8을 적용한다. Flyway가 꺼진 dev 환경에서는 V7까지 적용된 DB에 V8을 한 번 적용해야 한다.
JDBC 전용 테이블이므로 Hibernate ddl-auto로 생성되지 않는다.

## 정상 / partial / failed / rejected

유효한 Agent 입력은 다음 조건을 모두 만족해야 한다.

1. ID 연결 일치
2. analysisState=COMPLETED
3. status=success
4. KEYPAD → COMPLETE_KEYPAD, AUDIO → COMPLETE_AUDIO
5. 오류가 있는 모순된 success 결과가 아님

| outcome | 작업 상태 | 세션 상태 | eligible_for_agent / fallback_required |
|---|---|---|---|
| VALID | SUCCEEDED | COMPLETED | true / false |
| PARTIAL | FALLBACK_REQUIRED | FAILED | false / true |
| FAILED | FALLBACK_REQUIRED | FAILED | false / true |
| REJECTED | FALLBACK_REQUIRED | FAILED | false / true |

partial 또는 PARTIAL_SAMPLED_TIME_AXIS는 완전 분석으로 인정하지 않는다.
잘못된 coverage, nonterminal FINAL_RESULT, ID 불일치, 모순된 FAILED status/success result는 REJECTED다.
404·네트워크 timeout·파싱 실패처럼 FINAL_RESULT 자체가 없는 경우 final 행은 만들지 않고
작업의 FALLBACK_REQUIRED와 reason으로 실패를 보존한다.

이번 단계는 **저장과 세션 처리 상태 갱신만** 한다.
Agent AI, SecurityEvent 생성/변경, 알림, 경고음, MQTT, MP4/S3, LEGACY fallback을 실행하지 않는다.
EARLY_EVENT API·저장·중복 제거 및 기존 장치/Callback 인증 구현은 변경하지 않았다.

## 테스트 및 재현

HTTP 모킹은 추가 의존성 없이 JDK HttpServer를 사용한다. 실제 HTTP 200/202/404/408/429/5xx와
읽기 timeout/연결 실패를 검증한다. 단위 테스트는 두 trigger의 완전성 조합과 연결 ID,
nullable/유연한 이벤트 객체/엄격한 JSON 타입을 확인한다.

H2 테스트는 V6~V8 SQL에서 MySQL charset/collation 선언만 제거하여 실행하고,
동시 종료 통지·동시 점유·lease 복구·오래된 워커 차단·부분/실패 저장·원자적 롤백을 검증한다.
실제 MySQL 테스트는 새 disposable MySQL 8.0.43에서만 실행한다.

```powershell
./gradlew.bat test --tests '*VisionPolling*' --tests '*StreamingVision*Test' --tests '*VisionSessionCompletionApiTest'
$env:OMNIGUARDY_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:<temporary-port>/validation?serverTimezone=UTC'
./gradlew.bat build
```

2026-11-02 실행 결과:

- 전체 build 성공: **289개 중 287개 통과, 실패 0, 기존 skip 2**.
- 신규 단위/API/H2/HTTP 테스트 31개 통과. MySQL 통합 테스트 5개와 Swagger 검증도 통과.
- MySQL 8.0.43의 새 임시 DB에서 V1~V8 전체 migration 적용 및 Hibernate validate 성공.
  4개 동시 종료 통지의 한 작업 생성, FINAL_RESULT 중복 저장 방지,
  최종 작업 갱신 오류 시 결과 insert와 세션 변경의 동시 롤백을 검증했다.
- 기존 Vision 세션, EARLY_EVENT, LEGACY 영상 API/UseCase/Adapter,
  AUDIO/KEYPAD, MQTT 어댑터, Agent/알림 관련 테스트도 통과.
- 기존 skip은 `BackendApplicationTests` (Disabled 환경 의존 smoke test),
  `MqttAuthenticationIntegrationTest` (전용 테스트 브로커 환경 변수 미설정)다.
- `git diff --check` 통과. V1~V7과 보호 대상 LEGACY·EARLY_EVENT·트리거 구현 변경 없음.
- 이번에 만든 검증 컨테이너만 정리했으며 기존 dev MySQL/Mosquitto는 사용하거나 변경하지 않았다.

## 추가·수정 파일

아래 Java 경로의 기준은 `src/main/java/com/omniguardy/backend/domain/visionsession/`이다.

추가:

- `domain/model/VisionAnalysisState.java`, `VisionAnalysisCompleteness.java`
- `domain/model/VisionBinding.java`, `VisionPollingJob.java`, `VisionProcessingState.java`
- `domain/model/StreamingVisionStatus.java`, `StreamingVisionFinalResult.java`, `VisionFinalOutcome.java`
- `domain/error/StreamingVisionException.java`
- `application/model/VisionPollingPolicy.java`, `VisionProcessingView.java`
- `application/port/in/CompleteVisionSessionInputPort.java`
- `application/port/out/StreamingVisionQueryPort.java`, `VisionPollingRepository.java`
- `application/usecase/CompleteVisionSessionService.java`, `PollStreamingVisionService.java`
- `infrastructure/persistence/JdbcVisionPollingRepository.java`
- `infrastructure/streaming/StreamingVisionHttpAdapter.java`, `StreamingVisionJsonMapper.java`
- `infrastructure/streaming/StreamingVisionConfiguration.java`, `StreamingVisionPollScheduler.java`
- `presentation/VisionSessionCompletionController.java`
- `src/main/resources/db/migration/V8__create_vision_polling_and_final_results.sql`
- `src/test/java/com/omniguardy/backend/domain/visionsession/StreamingVisionTestSupport.java`
- 같은 test 경로의 `StreamingVisionFinalResultTest.java`, `StreamingVisionHttpAdapterTest.java`,
  `VisionPollingPersistenceTest.java`, `VisionSessionCompletionApiTest.java`
- `VISION_STREAMING_FINAL_RESULTS.md` (이 문서)

수정:

- `presentation/VisionSessionSuccessCode.java`: 202 success code 추가
- `src/main/resources/application.yml`: 독립 Streaming 설정
- `.env.example`, `deploy/.env.prod.example`: URL·워커·재시도 설정 예시
- `src/test/java/com/omniguardy/backend/global/config/SwaggerTestApplication.java`
- `src/test/java/com/omniguardy/backend/global/config/SwaggerEndpointIntegrationTest.java`
- `src/test/java/com/omniguardy/backend/global/config/ProdDatabaseIntegrationTest.java`

사용자가 제공한 `SPRING_BOOT_JSON_SPEC.md`는 수정하지 않았다.

## 다음 단계 연결 시 주의

- Agent는 eligible_for_agent=true만 소비하고 EARLY_EVENT·partial·failed·rejected를 완전 결과로 사용하지 않는다.
  relatedEarlyEventInstanceIds는 연관 정보이며 FINAL_RESULT 중복 키가 아니다.
- 향후 Agent/fallback 호출은 이번 저장 transaction 내부에서 직접 실행하지 않는다.
  별도 durable outbox/소비 이력과 세션별 멱등성 키를 추가해 재시작 시 외부 동작의 중복을 방지해야 한다.
- FALLBACK_REQUIRED는 요청 상태를 기록한 것이며 fallback 실행 완료를 의미하지 않는다.
  실패 사유를 확인한 뒤 fallback 작업을 별도로 점유/진행/완료 처리해야 한다.
- KEYPAD는 여기까지 SecurityEvent가 없다. 향후 생성 규칙을 명시적으로 적용해야 한다.
  AUDIO는 기존 SecurityEvent 연결을 유지해야 한다.
- finalResultCallbackEnabled=false와 polling 사용을 Vision 담당자/Pi 설정에서 유지한다.
  TTL보다 짧은 maxWait와 실제 Streaming URL을 설정하고, 모든 서버 시계를 동기화한다.
