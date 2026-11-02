# Streaming FINAL_RESULT → 기존 Agent AI 연결

## 기존 구조 분석과 재사용

`ReceiveVideoUseCase`는 LEGACY MP4 분석·저장 후 SecurityEvent에 Vision 결과를 반영한다.
AUDIO는 기존 eventId를 사용하고, 위험 판단 대상일 때 `AnalyzeAgentRiskUseCase.analyze(eventId)`를 호출한다.
KEYPAD는 감지 이벤트가 있을 때 `RecordKeypadVisionInputPort`로 이벤트를 기록하고,
`VisionAnalysis.hasHighRiskKeypadEvent()`가 참일 때 Agent를 호출한다.

`RecordKeypadVisionUseCase`는 `KEYPAD:<triggerId>`의 deterministic UUID로 SecurityEvent를 생성한다.
빈 이벤트 배열은 이벤트를 생성하지 않고 반환한다. 이번 연결은 이 UseCase를 그대로 호출한다.

`AnalyzeAgentRiskUseCase`의 기존 동작도 그대로 사용한다.

1. VISION_ANALYZED인 SecurityEvent를 읽어 기존 AgentContext를 생성한다.
2. 기존 RiskAssessmentPort 및 LangChain Adapter로 Agent를 호출한다.
3. 기존 필수 위험도 규칙을 적용하여 위험도·점수·사유를 SecurityEvent에 저장하고 COMPLETED로 갱신한다.
4. 기존 RiskResponseService로 대응한다. MEDIUM은 FCM, HIGH/CRITICAL은 FCM 및 경고음을 실행한다.
   LOW는 기록만 한다. 경고음은 기존 PlaySoundUseCase → SoundCommandPort → MQTT 경로다.
5. 기존 실패 시 FAILED 저장, 실행·LLM 지표 기록도 유지된다.

현재 RiskResponseService는 TTS를 호출하지 않는다. 별도 PublishTtsUseCase는 기존 경로에 남겨 두었고,
이번 연결에 새 TTS 호출이나 경고음 정책을 추가하지 않았다.

다음 파일은 이번 단계에서 변경하지 않았다: ReceiveVideoUseCase, VisionFastApiAdapter,
AnalyzeAgentRiskUseCase, AgentContext, Agent 프롬프트 및 Adapter, RecordKeypadVisionUseCase,
RiskResponseService, FCM/MQTT/TTS 구현, V1~V8 SQL 및 V6~V8 처리 코드.

## 처리 흐름

별도 Agent scheduler가 **commit된** V8 결과를 조회한다. V8 저장 transaction 안에서 Agent를 호출하지 않는다.

- eligible_for_agent=true / outcome=VALID / analysis_state=COMPLETED / result_status=success
- KEYPAD는 COMPLETE_KEYPAD, AUDIO는 COMPLETE_AUDIO
- fallback_required=false, Vision 세션 COMPLETED 및 polling 작업 SUCCEEDED
- 세션과 polling의 triggerType·securityEventId·KEYPAD triggerId 연결 일치

이 조건을 통과한 세션만 V9 작업으로 등록한다. EARLY_EVENT 테이블은 조회하지 않는다.
이후 V8 raw_result_json을 다시 파싱하여 전체 연결 ID·최종 상태·완전성도 재검증한다.
PARTIAL/FAILED/REJECTED는 작업 및 Agent/SecurityEvent 처리를 시작하지 않는다.

새 연결은 기존 `/api/edge/video`를 호출하지 않고 MP4/S3 작업을 하지 않는다.
종료 통지 API·인증·Vision polling·EARLY_EVENT 동작은 그대로다.

## 입력 매핑과 계약 차이

| Streaming FINAL_RESULT | 기존 입력으로의 연결 |
|---|---|
| sessionId 및 trigger의 triggerId/securityEventId/triggerType | 세션·polling 기대값과 대조; AUDIO는 vision_sessions.eventId 사용 |
| result.trackedPersonCount | 기존 VisionObservations.trackedPersonCount → personCount와 같은 추적 고유 인원 수 |
| result.visionEvents[].eventType | 기존 VisionEvent.eventType 그대로 |
| confidence | 필드가 제공될 때만 사용; 없으면 null |
| detectedFrame | 기존 Integer 범위 내에서 그대로 전달; 범위 초과 시 차단 |
| confirmedFrame / capturedAt / 기타 이벤트 필드 | details.streamingEvent에 원래 이벤트 전체 보존 |
| 원래 details | 기존 details에도 유지; 원문 전체는 details.streamingEvent에 보존 |
| prediction / behavior / classProbabilities | Streaming에 없으므로 생성하지 않음. DB prediction/confidence/class_probabilities는 null |
| analysisMode | 이벤트 기반 기존 규칙을 선택하는 호환 모드 `exhibition_rules`; Vision이 보낸 classifier 결과를 의미하지 않음 |
| video / duration / S3 path | 없으므로 null, 별도 저장 호출 없음 |
| emittedAt | 결과 발행 시각으로 vision_analyzed_at에 기록; 촬영 시각으로 해석하지 않음 |
| relatedEarlyEventInstanceIds / streamingPocScope / raw fields | V8 원문에 계속 보존; Agent 계약에 새 필드를 추가하지 않음 |

명세의 trackedPersonCount는 최종 tracking 고유 ID 수다. 기존 코드도
VisionObservations.trackedPersonCount를 VisionAnalysis.personCount로 전달하므로 의미가 일치한다.
0은 정상 관측값이며 그대로 전달한다.

기존 AgentContext.Vision.personCount는 int이고 기존 AnalyzeAgentRiskUseCase는 null을 0으로 바꾼다.
사용자 선택에 따라 **기존 계약과 코드는 변경하지 않고**, Streaming의 null은 호출 전에 차단한다.
`BLOCKED / UNKNOWN_PERSON_COUNT / fallback_required=true`로 기록하여 향후 fallback 대상으로 보존한다.
빈 이벤트 배열이라도 null이면 같은 정책이다. 자동 재시도·SecurityEvent 생성·Agent 호출은 하지 않는다.

기존 Agent가 null class_probabilities를 빈 map으로 읽는 동작은 재사용한다.
이는 분류 값이 없다는 뜻이며 어떤 class나 probability도 추가하지 않는다.
기존 prompt 및 필수 위험도 규칙은 변경하지 않았다.

이벤트 지원 여부도 기존 VisionEvent의 isHighRiskKeypadEvent / isExhibitionRulesEvent로 검사한다.
기존 규칙으로 판단할 수 없는 새 이벤트 유형은 UNSUPPORTED_EVENT로 차단하고 future fallback 플래그를 남긴다.
새 위험도 매핑을 임의로 추가하거나 일부 이벤트를 조용히 버리지 않는다.

## SecurityEvent 규칙

- AUDIO: 기존 eventId/triggerId/triggerType/triggeredAt을 검증하고 원래 SecurityEvent를 갱신한다.
  Audio 감지 결과·사용자·영상 경로는 보존한다. 새 SecurityEvent는 생성하지 않는다.
  감지 이벤트가 없으면 기존 requiresAudioRiskAssessment 규칙에 따라 completeWithoutRisk로 끝낸다.
- KEYPAD: 유효한 이벤트가 없으면 SecurityEvent를 만들지 않는다.
  이벤트가 있을 때만 기존 RecordKeypadVisionInputPort를 호출한다. 원래 발급된 keypad triggered_at을 사용한다.
  기존 deterministic eventId를 사용하므로 준비 단계 재시작도 같은 이벤트를 재사용한다.
- 이미 COMPLETED인 이벤트는 덮어쓰거나 Agent를 다시 호출하지 않는다.
- 최초 AUDIO 연결이 CAMERA_REQUESTED가 아니거나 이미 다른 분석이 소유한 이벤트면 차단한다.
  재시도에서는 이 이력에 연결된 이벤트와 원래 Streaming 증거가 유지되는지도 확인한다.

외부 동작은 새로 구현하지 않았다. 기존 AnalyzeAgentRiskUseCase가 저장과 RiskResponseService를 실행한다.

## V9 처리 이력과 트랜잭션

V9는 `streaming_agent_jobs`만 추가한다. V1~V8과 기존 테이블 구조는 변경하지 않는다.

- session_id PK/FK → vision_final_results: 한 FINAL_RESULT당 한 처리 이력
- event_id nullable UNIQUE/FK → security_events: 같은 SecurityEvent의 중복 연결 방지
- processing_state, attempts, agent_attempts, next_attempt_at
- lease_token, lease_until: 점유와 오래된 준비 작업 차단
- created_at / finished_at / reason / last_error / response_state / fallback_required
- 상태·시도 횟수 CHECK 및 재시도/lease 조회 인덱스

| 상태 | 의미 |
|---|---|
| PENDING | 최초 대기 또는 확인된 실패 후 재시도 대기 |
| PREPARING | 결과 매핑 및 SecurityEvent 준비 중 |
| CALLING | Agent 호출 시작을 DB에 기록한 상태 |
| SUCCEEDED | 기존 SecurityEvent에 Agent 판단 저장 확인 |
| SKIPPED | NO_EVENTS / NO_RISK / ALREADY_COMPLETED |
| BLOCKED | null 인원 수·지원 불가 이벤트·연결 오류; fallback 필요 플래그, 자동 재시도 없음 |
| FAILED | 영구 Agent 오류 또는 재시도 소진 |
| UNCERTAIN | 실행 중 중단되어 결과/대응 여부를 확정할 수 없음; 자동 재호출 없음 |

준비 단계는 별도 REQUIRES_NEW transaction이다. V9 행을 잠그고 토큰을 검증한 뒤
기존 이벤트 기록/갱신과 이력의 event_id 연결을 함께 commit한다. 중간 실패는 모두 롤백한다.
CALLING 전이는 별도 짧은 transaction에서 commit한 뒤 기존 Agent를 **DB transaction 없이** 호출한다.
실제 Agent 결과 저장과 외부 대응은 기존 UseCase의 transaction 경계를 그대로 따른다.
최종 이력 저장도 별도 transaction이다.

## 재시도·동시 실행·재시작 정책과 보장 범위

- 동시 발견은 session PK, 동시 실행은 조건부 UPDATE·점유 토큰으로 제거한다.
- PREPARING에서 중단되면 lease 만료 후 안전하게 다시 준비한다. 생성된 이벤트와 이력 연결은 원자적이다.
- 네트워크/timeout/429/5xx 등으로 **기존 UseCase가 FAILED 저장까지 마친 확인된 실패**만 재시도한다.
  해당 분기에서는 아직 RiskResponseService가 실행되지 않았으므로 후속 대응 중복을 만들지 않는다.
- 영구 입력/Agent 오류는 실패로 종료한다. 기본 전체 시도 한도는 3회이고 간격은 10초다.
  SDK 내부 retry 설정은 기존대로이며 agent_attempts는 기존 UseCase의 호출 횟수다.
- 만료된 CALLING은 자동 재점유하지 않는다. SecurityEvent에 판단이 저장되어 있으면 SUCCEEDED로 복구하고
  그렇지 않으면 UNCERTAIN으로 보류한다. 종료되지 않은 원래 워커가 나중에 성공하면 같은 토큰으로 성공을 확정할 수 있다.
- 이미 저장된 Agent 결과를 재시작 후 발견해도 FCM/경고음을 재실행하지 않는다.
- 기존 RiskResponseService의 best-effort 대응을 유지하므로 전송 예외는 기존대로 로그에 기록하고,
  전체 Agent 판단을 다시 실행하지 않는다.

기존 FCM/MQTT에는 서버 간 delivery 멱등성 키나 결과 저장과 전송을 묶는 원자적 계약이 없다.
따라서 프로세스가 결과 저장과 외부 전송 사이에 죽었을 때 **전송 성공 exactly-once는 보장할 수 없다**.
이번 연결은 불확실한 실행을 자동 재전송하지 않는 정책으로 중복을 방지한다.
이 경우 알림이 누락되었을 가능성이 있으며 response_state=UNKNOWN_NOT_REPLAYED로 명시한다.
BEST_EFFORT_ATTEMPTED도 전송 성공 확인이 아니라 기존 대응 경로를 호출했다는 뜻이다.
UNCERTAIN의 수동 재처리는 기존 워커 종료·저장 결과·외부 전송 여부 확인 후 별도 운영 절차가 필요하다.

이 보장은 Streaming 소비 경로에 적용한다. 같은 이벤트에 LEGACY MP4 업로드를 병렬 전송하는 방식은
기존 LEGACY 흐름에 새 소유권 계약을 추가하지 않은 이상 조정할 수 없으므로 사용하지 않는다.
이번 변경도 `/api/edge/video`로 fallback을 보내지 않는다.

## 설정

자동 워커는 STREAMING_AGENT_ENABLED=true와 기존 AGENT_AI_ENABLED=true를 모두 만족할 때만 실행된다.
Agent 기능이 꺼져 있으면 결과/처리 이력은 보존되고 자동 Agent 처리를 시작하지 않는다.

| 환경 변수 | 기본값 |
|---|---|
| STREAMING_AGENT_ENABLED | true |
| STREAMING_AGENT_PREPARATION_LEASE | 30s |
| STREAMING_AGENT_CALLING_LEASE | 5m |
| STREAMING_AGENT_RETRY_DELAY | 10s |
| STREAMING_AGENT_MAX_ATTEMPTS | 3 (1~10) |
| STREAMING_AGENT_BATCH_SIZE | 10 (1~100) |
| STREAMING_AGENT_SCHEDULER_DELAY_MS | 1000 |

Spring prefix는 omniguardy.streaming-agent다. Agent용 scheduler와 기존 Vision polling용 scheduler를
분리해 느린 LLM 요청이 Vision polling을 막지 않는다. 기본 Agent timeout·SDK retry·FCM 지연을 고려해
calling lease를 설정해야 한다. 다중 인스턴스의 시계는 UTC로 동기화한다.
prod Flyway가 V9를 적용한다. Flyway가 꺼진 dev DB에는 V8까지 적용한 후 V9를 별도로 적용해야 한다.

## 향후 fallback 연결

V8 FALLBACK_REQUIRED와 V9의 fallback_required를 함께 조회해야 한다.

```sql
SELECT s.session_id, p.reason AS vision_reason, a.reason AS agent_input_reason
FROM vision_sessions s
JOIN vision_polling_jobs p ON p.session_id = s.session_id
LEFT JOIN streaming_agent_jobs a ON a.session_id = s.session_id
WHERE p.processing_state = 'FALLBACK_REQUIRED' OR a.fallback_required = TRUE;
```

null 인원 수는 V8 원문/검증 결과를 변경하지 않고 V9에 입력 호환 불가 및 fallback 필요를 기록한다.
Agent 네트워크 실패와 UNCERTAIN은 Vision 분석 실패와 구별한다.
이번 단계에는 MP4 수신/저장 전용 API, fallback 소비자, 실제 LEGACY 재분석 실행이 없다.
향후에는 별도 MP4 보관·연결과 fallback 처리 이력/멱등성을 설계해야 한다.

## 변경 파일

아래 Java 경로는 `src/main/java/com/omniguardy/backend/` 기준이다. 이전 단계의 미커밋 파일은 유지했다.

추가:

- `domain/securityevent/application/port/in/PrepareStreamingVisionInputPort.java`
- `domain/securityevent/application/usecase/PrepareStreamingVisionService.java`
- `domain/securityevent/domain/error/StreamingVisionPreparationException.java`
- `domain/visionsession/domain/model/StreamingAgentJob.java`, `StreamingAgentSource.java`
- `domain/visionsession/domain/error/StreamingAgentInputException.java`
- `domain/visionsession/application/model/StreamingAgentPolicy.java`
- `domain/visionsession/application/port/out/StreamingAgentRepository.java`, `StreamingAgentMappingPort.java`,
  `StreamingAgentEventPort.java`, `StreamingAgentFailureClassifier.java`
- `domain/visionsession/application/usecase/ProcessStreamingAgentService.java`
- `domain/visionsession/infrastructure/persistence/JdbcStreamingAgentRepository.java`
- `domain/visionsession/infrastructure/agent/StreamingAgentVisionMapper.java`, `ExistingStreamingAgentAdapter.java`,
  `ExistingAgentFailureClassifier.java`, `StreamingAgentConfiguration.java`, `StreamingAgentScheduler.java`
- `src/main/resources/db/migration/V9__create_streaming_agent_jobs.sql`
- `src/test/java/com/omniguardy/backend/domain/visionsession/StreamingAgentMappingTest.java`
- `src/test/java/com/omniguardy/backend/domain/visionsession/StreamingAgentIntegrationTest.java`
- `VISION_STREAMING_AGENT.md` (이 문서)

수정:

- `src/main/resources/application.yml`: 워커 설정 추가
- `.env.example`, `deploy/.env.prod.example`: 워커 설정 예시
- `src/test/java/com/omniguardy/backend/global/config/ProdDatabaseIntegrationTest.java`: V9 및 MySQL 이력 점유/복구 테스트

## 검증 방법

Agent와 알림·경고음의 외부 경계는 모킹하고 기존 실제 UseCase·JPA·JDBC·transaction을 연결하여 검증한다.
테스트에서 실제 LLM·FCM·MQTT 전송은 하지 않는다.

```powershell
./gradlew.bat test --tests '*StreamingAgent*Test'
$env:OMNIGUARDY_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:<temporary-port>/validation?serverTimezone=UTC'
./gradlew.bat build
```

2026-11-02 최종 실행 결과:

- 전체 build 성공: **318개 중 316개 통과, 실패 0, 기존 skip 2**.
- 신규 매핑 테스트 5개 및 실제 기존 UseCase/JPA/JDBC 연결 테스트 23개 통과.
- 새 MySQL 8.0.43 DB에서 V1~V9 migration 적용 및 Hibernate validate 성공.
  실제 MySQL 통합 테스트 6개 모두 통과. V9의 동시 등록·단일 점유·만료된 CALLING의 자동 재점유 금지 검증 포함.
- null 인원 수의 영구 보류와 future fallback 플래그, 유효한 0의 Agent 전달,
  AUDIO 원래 이벤트 유지, KEYPAD 빈 이벤트 미생성, partial/failed/rejected Agent 미호출을 검증했다.
- 동시 워커, 준비 후 재시작, Agent 성공 직후 이력 저장 실패, 불확실한 실행의 비재호출,
  준비 transaction 롤백, 유한 네트워크 재시도, best-effort 대응 실패 시 비재실행을 검증했다.
- 기존 LEGACY, Agent 규칙/프롬프트 관련, FCM/MQTT/TTS 관련 및 V6~V8 회귀 테스트도 통과했다.
- skip: 기존 Disabled BackendApplicationTests와 전용 테스트 브로커 환경 변수가 없는 MqttAuthenticationIntegrationTest.
- `git diff --check` 통과. 기존 dev MySQL/Mosquitto는 사용하거나 변경하지 않았고 검증용 컨테이너만 정리했다.
