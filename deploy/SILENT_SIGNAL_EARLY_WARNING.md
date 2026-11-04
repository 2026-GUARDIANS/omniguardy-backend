# Silent Signal EARLY_EVENT 조기 경고

## 기존 계약과 분석 결과

- 실제 전달 경로는 `POST /api/internal/ai/vision/early-events` callback이다. `X-Vision-Callback-Key`로 인증한다. Polling이나 DB 외부 조회로 EARLY_EVENT를 받지 않는다.
- `VisionEarlyEventRequest`는 `messageType=EARLY_EVENT`, `provisional=true`, `event.eventType=SILENT_SIGNAL`을 허용한다. Vision에서 확정한 감지 관측이며 최종 분석 결과는 아직 잠정 상태라는 기존 계약을 그대로 사용한다. 새로운 confidence 임계값이나 Vision API 변경은 없다.
- 기존 `ReceiveVisionEarlyEventService`는 session/trigger/event 연결을 검증하고 `vision_early_events`에 저장했다. `(session_id,event_instance_id)` 중복은 동일 payload이면 성공, 변경된 payload이면 409였다. 기존에는 외부 경고를 실행하지 않았다.
- AUDIO는 실제 `SecurityEvent`와 발행된 trigger가 모두 일치해야 한다. KEYPAD는 발행된 trigger를 검증하고 callback의 `securityEventId`는 계속 NULL이어야 한다.

## 구현된 흐름

1. 기존 인증, 엄격한 JSON 계약, 연결 검증, 불변 callback 저장을 수행한다.
2. `VisionEarlyWarningPort` → `ExistingVisionEarlyWarningAdapter` → `DispatchEarlyWarningInputPort`로 경고를 요청한다. 동일 callback 재전송도 누락된 발송 예약을 복구한다.
3. `JdbcSecurityResponseLedger`가 검증 후 저장된 EARLY_EVENT를 다시 조회해 실제 trigger 단위의 FCM/SOUND 작업을 원자적으로 예약한다. 멱등성 키는 `AUDIO:{triggerId}` 또는 `KEYPAD:{triggerId}`다. sessionId를 eventId로 취급하지 않는다. 서로 다른 eventInstanceId라도 같은 trigger의 조기 경고는 한 번이다.
4. 전용 FCM Worker와 SOUND Worker를 각각 즉시 깨운다. 별도의 단일 스레드 executor와 제한된 대기열을 사용하므로 FCM이 느리거나 실패해도 SOUND Worker를 점유하지 않는다. callback은 외부 전송 완료를 기다리지 않고 기존 204를 반환한다.
5. `EarlyWarningDeliveryPort` Adapter가 기존 `NotificationService` → `PushNotificationPort`로 조기 알림을 발송하고, 기존 `SoundCommandPort.send(SoundType.SIREN)`으로 `door/sound` 명령을 발행한다. MQTT payload/발행 정책은 재사용한다.
6. 1초 간격의 별도 복구 스케줄러가 저장 후 예약이 누락된 관측, 아직 PENDING인 작업, 나중에 생성된 KEYPAD 이벤트 연결을 복구한다. 기존 Vision/Agent/Fallback Worker의 스케줄과 재시도는 변경하지 않는다. 간격은 `omniguardy.early-warning.recovery-delay-ms`로 설정할 수 있다.

조기 알림은 “긴급 조기 경고”, “Silent Signal이 감지되었습니다. … 최종 분석은 진행 중입니다.”라는 내용이다. FCM의 기존 `type=SECURITY_EVENT`, `riskLevel=3` 형식을 재사용하지만 `SecurityEvent.riskLevel`, status, Vision 증거, Agent 판단을 변경하지 않는다.

### AUDIO / KEYPAD 이벤트 연결

- AUDIO: 기존 eventId 및 해당 이벤트의 소유자를 사용한다. Adapter에서도 event의 triggerType/triggerId를 재검증한다.
- KEYPAD: 최종 Vision 처리 전에는 SecurityEvent가 없을 수 있다. 기존 `DemoUserResolver`의 소유자 규칙을 사용해 경고하되, 임의 eventId나 SecurityEvent를 생성하지 않는다. 이때 FCM data의 eventId는 기존 nullable 계약에 따라 생략된다. 최종 처리에서 실제 이벤트가 생성되면 검증된 trigger 관계로 발송 기록에 eventId를 연결한다. 이미 전송된 FCM 메시지의 data는 소급 변경되지 않는다.
- `vision_early_events.security_event_id` 및 KEYPAD MP4 이력의 NULL 제약을 변경하지 않는다. 기존 MP4 저장, video_path 연결 및 video-url API도 변경하지 않는다.

## 멱등성, 장애와 최종 Agent 연계

`V12__create_security_response_ledger.sql`은 채널별 상태/수준/예약 token을 보관하는 테이블 1개를 추가한다. 저장소의 Flyway 파일만 추가했으며 운영 DB에 적용하지 않았다.

| 상태 | 의미 | 자동 재발송 |
|---|---|---|
| PENDING | 아직 외부 호출을 시작하지 않은 작업 | Worker/재시작 복구 시 실행 |
| CALLING | 외부 호출 직전에 DB 예약이 커밋됨 | 재발송하지 않음 |
| SUCCEEDED | FCM API 반환 또는 MQTT client handoff 반환 | 재발송하지 않음 |
| UNCERTAIN | 외부 호출 또는 호출 후 처리에서 오류 발생 | 재발송하지 않음 |
| SKIPPED | 소유자/활성 토큰 없음 또는 기존 저장 전용 데이터 | callback은 재발송하지 않음. 최종 Agent의 기존 대응은 가능 |

- DB 행 잠금과 채널별 token으로 동시 callback/Worker/Agent를 직렬화한다. 트랜잭션 밖에서 외부 서비스를 호출한다. 낮은 수준의 이전 호출 완료가 새 SIREN 예약 상태를 덮어쓰지 못한다.
- `RiskResponseService`는 발송 직전에 같은 ledger를 사용한다. 조기 level 3 대응이 PENDING/CALLING/SUCCEEDED/UNCERTAIN이면 최종 MEDIUM의 WARNING/FCM, HIGH/CRITICAL의 SIREN/FCM을 다시 실행하지 않는다. LOW는 기존처럼 외부 대응이 없으며 조기 경고를 취소하지 않는다.
- 최종 HIGH 대응이 먼저 실행되면 지연된 EARLY_EVENT도 중복 발행하지 않는다. 최종 MEDIUM 대응이 먼저 실행된 경우에는 EARLY_EVENT가 level 3 경고로 한 번 상향할 수 있다.
- 조기 FCM에 수신자/토큰이 없어 SKIPPED이면 최종 대응 시 다시 발송할 수 있다. 이미 성공한 SIREN은 반복하지 않는다.
- 한 채널의 예외는 다른 채널을 취소하지 않는다. 오류 메시지, 토큰, 개인정보, payload를 새 로그에 기록하지 않는다.
- 기존 FCM/MQTT Port에는 외부 중복 제거 키나 수신 확인 계약이 없다. 따라서 외부 효과의 exactly-once 전달은 보장할 수 없다. 전송 후 응답 유실이나 예약 직후 프로세스 종료에서는 CALLING/UNCERTAIN을 보존하고 자동 재전송을 하지 않는다. 누락 가능성과 중복 방지 사이에서 중복 방지를 선택한 정책이다. 이 상태는 운영 확인이 필요하며 자동으로 PENDING으로 되돌리면 안 된다.
- 기능 도입 전의 저장 전용 EARLY_EVENT는 migration에서 SKIPPED로 등록한다. 과거 관측 때문에 기능 활성화 직후 사이렌이 소급 실행되지 않는다. 같은 과거 trigger의 새 인스턴스도 조기 경고를 다시 켜지 않는다. 일반 최종 Agent 대응은 허용한다.

최종 Vision 저장/상태 전이, Agent 위험도 계산/AI 호출, MP4 저장 및 V11 Fallback 처리에는 변경이 없다. 최종 외부 대응 직전에 중복 예약 검사만 추가했다.

## 성능 로그

기존 `PerformanceLog` JSON 로거를 재사용한다.

| stage | 측정 범위 / 기준 |
|---|---|
| EARLY_EVENT_HTTP | Spring HTTP filter 진입 → callback 응답 |
| EARLY_EVENT_STORE | 검증 및 기존 callback 저장/재전송 확인 |
| EARLY_EVENT_FCM_REQUEST | 조기 FCM Adapter 실행, `System.nanoTime()` |
| EARLY_EVENT_MQTT_SIREN | 조기 Sound Adapter 실행, `System.nanoTime()` |
| EARLY_EVENT_TO_FCM | 최초 callback의 저장된 서버 수신 시각 → FCM 처리 반환 |
| EARLY_EVENT_TO_SOUND | 최초 callback의 저장된 서버 수신 시각 → MQTT 처리 반환 |
| FCM_SEND_REQUEST / MQTT_SOUND_COMMAND | 기존 실제 연동 Adapter 계측 |

`PerformanceLog.request()`에서 Spring HTTP filter 진입 시각을 보관하고 `vision_early_events.received_at`에 UTC로 기록한다. HTTP 밖에서 직접 저장한 경우에는 기존처럼 저장 시각을 사용한다. 재전송은 최초 시각을 유지한다. Worker 간 소요 시간은 이 저장된 UTC 시각으로 계산하며 Pi/Vision의 emittedAt이나 서로 다른 프로세스의 nanoTime을 비교하지 않는다.

로그 예시(식별자와 시간은 예시):

```json
{"schemaVersion":1,"timestamp":"2026-11-04T08:00:00.032Z","source":"SPRING","stage":"EARLY_EVENT_TO_SOUND","eventId":"event-example","triggerId":"trigger-example","sessionId":"session-example","triggerType":"AUDIO","durationMs":32.0,"status":"SUCCESS","attempt":1,"retryCount":0,"timingBasis":"PERSISTED_WALL_CLOCK","baseline":"EARLY_EVENT_RECEIVED_AT","observationPoint":"MQTT_CLIENT_HANDOFF","recipientDeliveryConfirmed":false}
```

FCM API 반환은 휴대폰 실제 수신을 의미하지 않는다. MQTT handoff는 Pi의 실제 수신이나 사이렌 재생 확인을 의미하지 않는다. 실패/timeout은 FAILURE/TIMEOUT으로 남는다. PENDING 조회가 비어 있으면 성능 로그를 추가하지 않는다.

수신자/토큰이 없어 FCM을 보내지 않은 경우에는 `EARLY_EVENT_FCM_REQUEST.resultCode`로 사유를 기록하고 `EARLY_EVENT_TO_FCM` 요청 완료 지연 로그를 생성하지 않는다.

운영자가 나중에 확인할 때는 기존 JSON 수집에서 `source=SPRING`, 위 stage, sessionId/triggerId로 필터링하면 된다. 운영 조회·접속·배포는 이번 작업에서 실행하지 않았다.

```sql
-- 운영 확인용 읽기 전용 예시. 이번 작업에서는 실행하지 않음.
SELECT response_key, event_id, session_id, fcm_state, sound_state, received_at, updated_at
FROM security_response_ledger
WHERE fcm_state IN ('PENDING','CALLING','UNCERTAIN')
   OR sound_state IN ('PENDING','CALLING','UNCERTAIN');
```

## 변경 파일

- callback 연계: `ReceiveVisionEarlyEventService`, `VisionEarlyWarningPort`, `ExistingVisionEarlyWarningAdapter`, `VisionEarlyEventController`(설명).
- 경고 application/port: `DispatchEarlyWarningInputPort`, `DispatchEarlyWarningService`, `SecurityResponseLedger`, `EarlyWarningDeliveryPort`.
- 외부 연동/영속화: `ExistingEarlyWarningDeliveryAdapter`, `EarlyWarningWorkers`, `JdbcSecurityResponseLedger`, `V12__create_security_response_ledger.sql`.
- 기존 발송 정책 연계: `RiskResponseService`, `NotificationService`의 조기 메시지 전용 메서드.
- 서버 수신 시간: `PerformanceLog`, `JdbcVisionEarlyEventRepository`.
- 테스트: `SilentSignalEarlyWarningIntegrationTest`, `ExistingEarlyWarningDeliveryAdapterTest`, `NotificationServiceTest`, 기존 `VisionEarlyEventServiceTest`/`VisionEarlyEventPersistenceTest`/`VisionEarlyEventApiTest`의 새 Port 연결.
- 기존 통합 테스트 fixture: `StreamingAgentIntegrationTest`, `VisionFallbackIntegrationTest`의 V12 생성/정리. `ProdDatabaseIntegrationTest`는 조기 발송 Port를 Mock으로 대체해 별도 실행 시에도 실제 조기 발송을 막는다.

## 검증

외부 서비스 호출 없이 H2 및 Mock을 사용한다. 실제 MySQL, Firebase, MQTT broker/device, EC2는 사용하지 않는다.

- AUDIO/KEYPAD, 이벤트 생성 전·후 연결, callback 재전송/다른 인스턴스, 동시 callback/Worker/최종 Agent 경합.
- 최종 처리를 기다리지 않는 발송, FCM 실패/지연 중 독립 SIREN, SIREN timeout 중 독립 FCM.
- PENDING 복구, 저장 후 예약 실패의 재전송 복구, CALLING 재시작 fencing, 이전 token의 늦은 완료 차단.
- 최종 HIGH 중복 방지, MEDIUM 이후 조기 상향, LOW가 조기 경고를 취소하지 않음, 토큰 없음의 최종 처리 기회 유지.
- 잘못된 연결/변경 payload 거부, 최초 HTTP 수신 시각 유지, 과거 저장 전용 관측의 소급 발행 차단.
- 기존 발송 Port 재사용, 새 Spring Worker bean 구성, 기존 EARLY_EVENT API/인증/저장 및 전체 회귀 테스트.

전체 로컬 회귀 빌드: **437개, 성공 426개, 실패 0개, skipped 11개**. Skipped는 외부 MySQL 환경을 요구하는 기존 테스트 9개, MQTT 환경을 요구하는 기존 테스트 1개, 기존 disabled smoke test 1개다. 새 테스트 사례는 28개다.

```powershell
$env:OMNIGUARDY_TEST_MYSQL_URL=$null
$env:OMNIGUARDY_TEST_MQTT_URL=$null
./gradlew.bat build --offline
```

`git diff --check`도 통과했다. MySQL 전용 동작은 이번 Mock/H2 검증 범위에 포함되지 않는다. 운영 DB migration 적용, 운영 배포, 실제 FCM/MQTT 발송, Git commit/push는 실행하지 않았다.

후속 배포 전 검토 및 최신 테스트 결과(450개 중 성공 439개, 실패 0개, skipped 11개)는 [배포 전 검토 문서](SILENT_SIGNAL_EARLY_WARNING_PREDEPLOY_REVIEW.md)에 정리했다. 수신자 없음 처리, KEYPAD trigger 대소문자 연결 수정, InnoDB 명시 및 추가 회귀 테스트를 포함한다.
