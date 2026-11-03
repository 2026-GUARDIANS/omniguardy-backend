# Raspberry Pi Streaming 연동 계약 점검

점검일: 2026-11-02. 저장소 코드와 로컬 자동화 테스트 기준이며, Raspberry Pi 소스 및 운영 서버의 실제 상태는 확인하지 않았다.

## 1. 이번 변경

AUDIO MQTT에는 이미 `triggerId`가 있었다. Raspberry Pi가 함께 요구하는 `eventId`가 누락되어 AUDIO에만 추가했다. 기존 필드의 이름, 값, 타입, topic, duration은 유지했다. 발행 시 실제 `SecurityEvent`의 `triggerId`, `eventId`, `triggeredAt`을 사용한다.

세션 등록/완료 API, worker, migration, LEGACY Vision/Agent 로직은 변경하지 않았다. 새 AUDIO Trigger 엔티티나 ID 발급 규칙도 만들지 않았다.

| 변경 파일 (`src/` 기준) | 내용 |
| --- | --- |
| `main/java/com/omniguardy/backend/domain/detection/application/service/AudioDetectionService.java` | 저장 대상 SecurityEvent의 실제 연결 값으로 CameraStartCommand 구성 |
| `main/java/com/omniguardy/backend/domain/detection/infrastructure/mqtt/MqttCameraCommandAdapter.java` | AUDIO에 eventId 별칭 추가, 기존 triggerId/securityEventId 유지 |
| `test/java/com/omniguardy/backend/domain/detection/infrastructure/mqtt/MqttCameraCommandAdapterTest.java` | 서로 다른 trigger/event ID 보존, 실제 AUDIO 동일 ID 규칙, KEYPAD 기존 필드 검증 |
| `test/java/com/omniguardy/backend/domain/securityevent/infrastructure/persistence/AudioResultIntegrationTest.java` | H2 저장 엔티티와 실제 직렬화 MQTT payload의 ID 연결 검증 |
| `test/java/com/omniguardy/backend/domain/visionsession/VisionSessionApiTest.java` | AUDIO eventId-only 등록 및 양쪽 ID 전송 시 400 계약 검증 |
| `test/java/com/omniguardy/backend/domain/visionsession/VisionSessionCompletionApiTest.java` | 임의 실패 body가 실패 통지로 처리되지 않는 현재 계약 검증 |
| `test/java/com/omniguardy/backend/domain/visionsession/VisionFallbackIntegrationTest.java` | complete 미통지 시 작업 없음, complete→polling 실패→MP4 fallback→Agent 경로 검증 |

보고서: `deploy/PI_STREAMING_CONTRACT.md`.

## 2. AUDIO 발행 흐름과 ID

기존 오디오 파일 업로드와 오디오 JSON 결과 수신은 `AudioDetectionService.process`를 공유한다.

1. BACKGROUND/쿨다운/중복 수신 조건을 기존 규칙대로 처리한다.
2. UUID 문자열 `eventId`를 발급하고 `SecurityEvent`를 저장한다.
3. `connectAudioTrigger(eventId, triggeredAt)`가 `SecurityEvent.triggerId`에 동일 UUID를 저장한다.
4. `CameraCommandPort` → `MqttCameraCommandAdapter` → `MqttGateway`로 CAMERA_START를 발행한다.
5. 이벤트 상태를 CAMERA_REQUESTED로 변경한다.

| 식별자 | 저장 위치/의미 |
| --- | --- |
| SecurityEvent 숫자 PK | `security_events.id`. 외부 triggerId/eventId로 사용하지 않음 |
| SecurityEvent 비즈니스 ID | `security_events.event_id`. MQTT eventId 및 securityEventId |
| 실제 AUDIO trigger ID | `security_events.trigger_id`. MQTT triggerId |
| 오디오 수신 중복 제거 ID | `audio_detection_receipts.detection_id`. CAMERA_START ID와 별개 |

현재 AUDIO에는 별도 Trigger 엔티티/테이블이 없고 SecurityEvent가 발급 트리거를 보관한다. 현재 발급 규칙에서 `triggerId == eventId`는 의도된 값이다. KEYPAD처럼 `trg-` UUID를 새로 만들어 대체하면 기존 연결을 깨뜨린다. Adapter 테스트는 두 값이 다를 때도 실제 triggerId를 보존하는지 별도로 확인한다.

기본 topic은 `door/camera`이며 `${mqtt.topic.camera}` 설정을 따른다. 예시 UUID는 설명용이다.

```json
{
  "type": "CAMERA_START",
  "duration": 10,
  "triggerId": "f30a06af-218e-4c87-bf10-6a7b8c9234ae",
  "triggerType": "AUDIO",
  "securityEventId": "f30a06af-218e-4c87-bf10-6a7b8c9234ae",
  "triggeredAt": "2026-11-02T19:00:00+09:00",
  "eventId": "f30a06af-218e-4c87-bf10-6a7b8c9234ae"
}
```

이전 payload는 위 예시에서 마지막 `eventId`만 없었다. 기존 촬영 명령 duration=10초와 AUDIO 쿨다운=12초는 변경하지 않았다. Pi의 실제 녹화 길이 정책과 별도로 합의해야 한다.

## 3. KEYPAD 및 LEGACY 호환성

`TriggerKeypadCameraService`는 `trg-UUID`를 발급하고 `keypad_camera_triggers`에 먼저 기록한 뒤 동일 CameraCommandPort로 발행한다. 이 시점에 SecurityEvent를 생성하지 않는다.

KEYPAD payload는 기존과 동일한 6개 필드를 유지한다. `securityEventId`는 JSON null이고 새 `eventId`는 생략된다.

```json
{
  "type": "CAMERA_START",
  "duration": 10,
  "triggerId": "trg-cc84ec84-6007-4c19-9365-249b5aa32ca8",
  "triggerType": "KEYPAD",
  "securityEventId": null,
  "triggeredAt": "2026-11-02T19:00:00+09:00"
}
```

KEYPAD는 triggerId만으로 시작해야 한다. AUDIO와 동일하게 eventId 필수를 강제하면 안 된다. Vision으로 전달하는 AUDIO securityEventId는 MQTT eventId와 같으며, triggerId는 MQTT에서 받은 값을 그대로 사용한다.

저장소에 `camera_listener.py`를 포함한 Python 소스가 없어 실제 Python 파서 실행 검증은 할 수 없었다. 추가 필드를 무시하고 기존 필드를 읽는 listener와 호환되는 additive 변경이다. JSON key 개수를 고정하거나 알 수 없는 key를 거부하는 listener라면 Pi 측 확인이 필요하다. `/api/edge/video`, ReceiveVideoUseCase, 기존 MP4 분석 Adapter는 수정하지 않았다.

## 4. 세션 등록 API 계약 (변경 없음)

`POST /api/edge/vision/sessions`

- 인증: `X-Device-Api-Key` (기존 장치 키, callback 전용 키와 별개).
- Content-Type: `application/json`.
- DTO: `sessionId` 필수 최대 128자, `triggerType` 필수 AUDIO/KEYPAD, `eventId` nullable 최대 36자, `triggerId` nullable 최대 64자.
- 사용 ID는 `[A-Za-z0-9][A-Za-z0-9._:-]*` 형식이어야 한다. sessionId는 대소문자를 구분한다.

AUDIO 요청:

```json
{
  "sessionId": "pi-audio-001",
  "triggerType": "AUDIO",
  "eventId": "f30a06af-218e-4c87-bf10-6a7b8c9234ae"
}
```

KEYPAD 요청:

```json
{
  "sessionId": "pi-keypad-001",
  "triggerType": "KEYPAD",
  "triggerId": "trg-cc84ec84-6007-4c19-9365-249b5aa32ca8"
}
```

**MQTT payload 전체를 등록 body로 복사하면 안 된다.** AUDIO는 eventId만 받고 triggerId는 생략/null이어야 한다. AUDIO에 둘 다 넣으면 현재 `400 INVALID_VISION_TRIGGER`다. KEYPAD는 triggerId만 받고 eventId는 생략/null이어야 한다. 빈 문자열은 null의 대체값이 아니다. 등록 DTO의 이름은 `eventId`이며 `securityEventId`로 대체할 수 없다.

AUDIO는 기존 이벤트가 AUDIO 트리거이며 유효한 실제 triggerId/triggeredAt과 CAMERA_REQUESTED 상태를 가져야 한다. Spring은 기존 SecurityEvent에서 실제 AUDIO triggerId를 찾아 callback, polling, MP4 연결을 검증한다. AUDIO 세션 저장/응답의 `triggerId`는 null이므로 Pi는 MQTT에서 받은 실제 triggerId를 보관해야 한다. KEYPAD는 발급된 트리거가 테이블에 있어야 한다.

| 상황 | HTTP / code |
| --- | --- |
| 신규 등록 | 201 / VISION_SESSION_REGISTERED, Location 헤더 제공 |
| 동일 sessionId + 동일 연결 정보 재전송 | 200 / VISION_SESSION_FOUND |
| 동일 sessionId + 다른 연결 정보 | 409 / VISION_SESSION_CONFLICT |
| 다른 sessionId로 이미 연결된 eventId/triggerId 재등록 | DB UNIQUE 충돌 시 409 / VISION_SESSION_CONFLICT |
| 발급 트리거/이벤트 없음 | 404 / VISION_TRIGGER_NOT_FOUND |
| AUDIO 상태 부적합/잘못된 ID 조합 | 400 / INVALID_VISION_TRIGGER |
| 잘못된 sessionId 등 형식 오류 | 400 |
| 장치 키 누락/불일치 | 401 |

동일 등록 재전송은 저장된 현재 상태/최초 등록 시간을 반환하며 terminal 상태여도 작업을 재시작하지 않는다. 다른 sessionId의 신규 요청은 트리거 검증이 먼저 실행되므로 AUDIO 상태가 이미 달라졌다면 UNIQUE 충돌 이전에 400이 될 수 있다. 409를 무조건 성공으로 취급하지 말고 원래 sessionId를 유지해 재시도하며, 필요 시 GET `/api/edge/vision/sessions/{sessionId}`로 연결 정보를 확인한다.

성공 응답 형식은 기존 `ApiResponse`다. 예시:

```json
{
  "success": true,
  "code": "VISION_SESSION_REGISTERED",
  "message": "Vision session registered.",
  "data": {
    "sessionId": "pi-audio-001",
    "triggerType": "AUDIO",
    "eventId": "f30a06af-218e-4c87-bf10-6a7b8c9234ae",
    "triggerId": null,
    "status": "REGISTERED",
    "registeredAt": "2026-11-02T10:00:01Z"
  }
}
```

## 5. Spring 촬영 종료 API 계약 (변경 없음)

`POST /api/edge/vision/sessions/{sessionId}/complete`

- 인증: X-Device-Api-Key.
- **요청 body 없음.** sessionId는 path로만 전달한다.
- 정상/동일 재전송 모두 `202 VISION_COMPLETION_ACCEPTED`.
- data: sessionId, processingState, requestedAt, deadlineAt, attempts, fallbackRequired, reason.
- 등록된 세션과 발급 트리거가 필요하다. 없는 세션/트리거는 404, 잘못된 ID는 400, polling job 없이 이미 terminal인 세션은 409다.
- 최초 요청이 영속 polling job을 만든다. 실제 Vision HTTP 호출은 worker에서 수행한다.
- 같은 sessionId의 job이 이미 있으면 terminal 상태를 포함해 현재 상태를 반환한다. job 수, attempts, deadline을 초기화하지 않는다.

`{"status":"failed","reason":"FRAME_UPLOAD_FAILED"}` 같은 JSON을 보내도 현재 Controller는 body를 읽지 않아 일반 polling을 시작한다. 이는 실패 보고 API가 아니다. 테스트로 현재 동작을 명시했으며 이런 body 사용을 권장하지 않는다.

정상 순서는 Pi→Vision `/vision/complete` 다음 Pi→Spring `/complete`다. Spring은 Vision의 `/vision/complete`를 대신 호출하지 않는다. 두 요청은 별개 서버에 대한 별개 요청이며 한쪽 성공이 다른 쪽 성공을 보장하지 않는다.

## 6. frame/complete 실패와 V11 fallback 시작 조건

| 상황 | 현재 Spring 동작 |
| --- | --- |
| Vision 프레임 전송 실패만 발생 | Spring에 직접 통지되지 않음. 등록/MP4만으로 fallback 생성 안 됨 |
| Spring complete가 접수되지 않음 | V8 job이 없어 polling 및 그에 따른 V11 fallback 시작 안 됨 |
| Spring complete 응답 유실, DB job은 생성됨 | worker 계속 실행. 동일 sessionId 재전송 안전 |
| Vision complete 실패/응답 유실, Spring complete 접수 | status polling으로 실제 상태 확인. Vision이 RECEIVING이면 원래 deadline까지 대기 |
| V8 Vision FAILED, PARTIAL/REJECTED 결과, 404/잘못된 응답 등 최종 실패 | FALLBACK_REQUIRED 기록 후 V11 발견 |
| V8 네트워크 오류/계속 PROCESSING/결과 202 | 재시도 후 원래 deadline에 도달하면 FALLBACK_REQUIRED |
| V9 입력 매핑 BLOCKED + fallback_required=true, Agent 미호출 | V11 대상 (UNKNOWN_PERSON_COUNT, UNSUPPORTED_EVENT 등) |
| V9 Agent 자체 오류/UNCERTAIN 또는 이미 처리 소유권 확보 | Vision 실패로 바꿔 임의 fallback 실행하지 않음 |

V11은 같은 sessionId의 `upload_state=STORED` MP4만 읽는다. 없으면 WAITING_VIDEO로 대기하고 설정 시간 후 VIDEO_WAIT_TIMEOUT이 된다. 기존 S3/로컬 원본→기존 MP4 Vision Adapter→기존 SecurityEvent/Agent 경로를 사용한다. V9/V11 소유권과 이벤트 단위 실행 이력에 따라 중복 Agent/대응을 제한하고 불명확한 실행 결과는 UNCERTAIN으로 관리한다.

기본 설정은 polling interval 2초, V8 max wait 5분, V11 retry delay 10초, video wait 10분이다. video wait는 fallback 작업 생성 시점부터 계산되며, Spring complete 재전송으로 연장되지 않는다. 운영 환경의 실제 주입 값이 우선한다. polling/fallback/Agent의 기존 활성화 설정도 필요하다.

프레임이 누락됐는데 Vision이 COMPLETED/success/COMPLETE_*를 보고하면 현재 Spring은 Pi의 전송 실패 이력을 모르므로 VALID로 처리할 수 있다. Pi 실패 여부만으로 결과를 배제하는 별도 계약은 없다.

## 7. 추가 합의/변경 필요 사항 (이번에는 미구현)

1. **Pi 등록 body 확인:** AUDIO MQTT에서 eventId와 triggerId를 모두 보관하되 Spring 등록에는 eventId만 전송한다. Pi가 등록에도 두 ID를 요구한다면 Pi 요청 분리 또는 Spring DTO/도메인/저장 계약 확장이 필요하다. 이번에는 API를 변경하지 않았다.
2. **실패 경로 보장:** frame/ Vision complete 실패로 Spring complete 및 원본 MP4 업로드가 생략되는 Pi 흐름이면 현재 fallback이 시작되지 않는다. Vision complete의 결과가 불명확하더라도 Spring polling 통지와 MP4 저장을 독립적으로 재시도하는 정책이 필요하다. 정상 순서는 유지하되 오류 경로를 별도 합의해야 한다. Spring 202를 받을 때까지 동일 sessionId로 완료 통지를 재시도하고, MP4도 동일 세션으로 재전송한다.
3. **명시적 실패 계약:** 즉시 fallback이 필요하면 인증/멱등성이 있는 별도 abort/failure 통지 또는 등록 후 완료 통지 미도착 watchdog을 설계할 수 있다. 현재 complete body에 임의 실패 필드를 추가해도 동작하지 않는다. 신규 경로도 기존 V9/V11 소유권 및 UNCERTAIN 규칙을 따라야 한다.
4. **커밋 전 MQTT 경합:** AUDIO/KEYPAD는 트리거 저장과 MQTT 발행을 같은 트랜잭션에서 수행한다. MQTT 발행 시점에는 DB 커밋 전이며 AUDIO는 이후 CAMERA_REQUESTED로 바뀐다. Pi가 즉시 등록하면 일시적인 404/상태 부적합 400을 받을 수 있다. 서버의 after-commit 발행/outbox가 근본적인 개선 방향이다. Pi는 발급 직후 알려진 경합에 한해 제한된 재시도를 고려하되 모든 400을 무조건 재시도하면 안 된다. 발행 실패/프로세스 종료까지 포괄하는 전달 보장은 현 구조에서 제공되지 않는다.
5. **실제 listener 검증:** 별도 저장소의 LEGACY camera_listener.py가 추가 JSON key를 허용하는지, KEYPAD eventId 부재/null 처리가 가능한지 확인한다. 녹화 시간 및 세션 ID를 재시도 간 유지하는지도 확인한다.
6. **영상 경로:** Streaming MP4는 `/api/edge/vision/sessions/{sessionId}/video`를 사용한다. `/api/edge/video`를 다시 호출하면 LEGACY 이중 분석을 만들 수 있다.

## 8. 테스트 결과

전체 `./gradlew.bat test`: **BUILD SUCCESSFUL**, 382건 중 372건 통과, 10건 skip, 실패/오류 0.

- skip: 운영 MySQL 검증 8건, 실제 MQTT 인증 통합 1건 (환경변수 미설정), 기존 disabled context 테스트 1건.
- 실행 PowerShell 자식 프로세스에서 OMNIGUARDY_TEST_MYSQL_URL / OMNIGUARDY_TEST_MQTT_URL을 비웠다.
- ID 영속성은 H2 통합 테스트, MQTT payload는 mock gateway를 통해 검증했다.
- fallback 검증은 mock Vision/S3/Agent/알림/경고음과 테스트 clock을 사용했다.
- 기존 LEGACY 영상 처리, Vision Adapter, AUDIO/KEYPAD, EARLY_EVENT 및 V6~V11 회귀 테스트를 함께 실행했다.
- 실제 EC2, MQTT broker, Vision/LLM, AWS S3, 운영 DB에 요청하지 않았다. 이미지 빌드/배포 및 migration 수정도 수행하지 않았다.
