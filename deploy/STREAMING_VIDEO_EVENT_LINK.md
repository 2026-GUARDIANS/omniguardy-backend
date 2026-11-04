# Streaming 영상과 SecurityEvent 연결

기존 S3 저장과 V10 저장 이력을 유지하면서 앱의 기존 `GET /api/security-events/{eventId}/video-url`로 영상을 조회할 수 있도록 영상 경로를 연결한다. 운영 배포나 운영 DB 직접 수정은 수행하지 않는다. 새 migration은 없다.

## 처리 흐름

1. 업로드는 기존 예약, S3 조건부 PUT, STORED 확정을 수행한다. 신규 업로드는 201, 같은 원본 재전송은 200이다.
2. 업로드 완료와 재전송에서 `LinkStreamingVideoInputPort.afterUpload`을 호출한다.
3. `JdbcStreamingVideoLinkRepository`는 STORED 영상, 등록 세션, Agent/fallback의 이벤트 연결을 함께 검증한다. AUDIO는 영상의 security_event_id와 세션 event_id가 같아야 한다. KEYPAD는 원래 두 event_id가 NULL이고 trigger_id가 같아야 하며, 후속 작업 이력의 event_id를 사용한다. Agent와 fallback의 event_id가 모두 있으면 서로 같아야 한다.
4. `SecurityEventStreamingVideoLinkAdapter`가 SecurityEvent 입력 Port를 호출한다. SecurityEvent 내부에서 eventId, triggerType, triggerId를 정확히 재검증한다. sessionId를 eventId로 사용하지 않는다.
5. 이벤트 행 잠금과 `UPDATE ... WHERE video_path IS NULL`로 경로만 연결한다. NULL이면 연결, 동일 경로이면 성공, 다른 경로이면 충돌이다. COMPLETED 이벤트도 허용한다. 상태·분석 결과·위험 판단은 변경하지 않는다.
6. KEYPAD 이벤트와 Agent 작업 연결이 커밋된 후 Spring AFTER_COMMIT 이벤트로 연결을 시도한다. 영상이 아직 없으면 이후 업로드에서 연결한다. 감지가 없으면 이벤트를 만들지 않는다. KEYPAD 영상 이력의 security_event_id NULL 제약은 유지한다.

S3 환경에서는 V10 `storage_path`의 객체 키를 그대로 사용한다. 파일명에서 키를 재구성하거나 presigned URL을 저장하지 않는다. 기존 video-url API가 소유권 확인 후 URL을 발급한다. 개발 LOCAL 영상은 기존 로컬 경로를 사용한다.

## 실패와 복구

- 연결 단계의 일시적 DB 오류는 이미 성공한 파일 저장을 실패 상태로 되돌리지 않는다. 저장 성공 응답을 유지하고 로그를 남긴다. 동일 파일 재전송 및 재조정에서 다시 시도한다.
- 기존 video_path가 다른 경우 업로드/재전송은 `409 STREAMING_VIDEO_EVENT_CONFLICT`를 반환한다. 영상의 STORED 이력과 기존 이벤트 경로를 모두 보존한다. 같은 파일을 반복 전송해도 다른 경로를 덮어쓰지 않는다.
- 검증된 연결이 다르면 `STREAMING_VIDEO_BINDING_MISMATCH`로 거부한다. 작업 이력 없이 triggerId만으로 이벤트를 추측하거나 새로운 이벤트를 만들지 않는다.
- 이벤트 커밋 후 연결 실패나 충돌은 Agent 작업의 성공/실패, AI 호출, FCM, 경고음에 영향을 주지 않는다. 재조정 로그에 남기고 계속 진행한다.
- 재조정은 배포 전의 STORED 행도 포함한다. 세션 ID로 정렬한 keyset 페이지를 순환하므로 앞쪽의 충돌/미연결 행이 뒤쪽 데이터 복구를 막지 않는다. 재시작하면 처음부터 다시 검사하며 연결 자체는 멱등적이다.
- 기존 MP4 fallback의 분석·저장·Agent 흐름은 유지한다. fallback이 이미 연결한 동일 경로는 성공으로 처리한다.

설정 기본값:

| 환경 변수 | 기본값 | 역할 |
|---|---|---|
| STREAMING_VIDEO_EVENT_LINK_ENABLED | true | 주기적 복구 활성화; 업로드 및 커밋 후 즉시 연결은 계속 수행 |
| STREAMING_VIDEO_EVENT_LINK_DELAY_MS | 30000 | 최초 실행 지연과 배치 간격(ms) |
| STREAMING_VIDEO_EVENT_LINK_BATCH_SIZE | 100 | 회차당 검사할 STORED 행 수 |

재조정은 독립된 단일 스레드 스케줄러를 사용하며 Vision polling이나 Agent 스케줄러를 점유하지 않는다. 전체 복구 시간은 STORED 행 수와 배치 설정에 따라 달라진다. 충돌 건은 자동 교체하지 않으므로 별도 원인 확인이 필요하다.

## 동시 저장 보호

Agent는 이벤트를 읽고 외부 AI 호출 뒤 저장한다. 그 사이 영상 연결이 완료될 수 있다. `SecurityEventRepositoryAdapter.save`는 기존 이벤트의 행을 잠그고 DB의 video_path를 확인한 뒤, 저장할 엔티티가 NULL 경로를 갖고 있으면 최신 경로를 보존한다. 잠금은 JPA merge/flush/commit까지 유지된다. 잠금 조회에는 COMMIT flush mode를 적용해 오래된 관리 엔티티가 조회 전에 자동 flush되지 않게 한다. 이벤트 연결도 같은 행 잠금을 사용한다.

Agent의 네트워크 호출 동안 DB 잠금을 잡는 구조는 추가하지 않았다. 기존 LEGACY의 명시적 영상 경로 설정은 유지한다.

## 주요 변경 파일

경로는 `src/main/java/com/omniguardy/backend/domain/` 기준이다.

| 위치 | 파일 / 역할 |
|---|---|
| visionsession/application/port/in | LinkStreamingVideoInputPort — 업로드/이벤트/복구 진입점 |
| visionsession/application/port/out | StreamingVideoLinkRepository, StreamingVideoEventLinkPort — 검증 메타데이터 조회 및 다른 도메인 연결 Port |
| visionsession/application/usecase | LinkStreamingVideoService — 멱등 연결·충돌·재조정; StreamingSessionVideoService — 업로드/재전송 연결 호출 |
| visionsession/infrastructure/persistence | JdbcStreamingVideoLinkRepository — 세션/영상/작업 이력 검증 |
| visionsession/infrastructure/video | SecurityEventStreamingVideoLinkAdapter, StreamingVideoLinkRequested, StreamingVideoLinkListener, StreamingVideoLinkScheduler, StreamingVideoConfiguration |
| visionsession/infrastructure/agent | ExistingStreamingAgentAdapter — 이벤트 연결 커밋 후 요청 발행 |
| securityevent/application | LinkSecurityEventVideoInputPort, LinkSecurityEventVideoService |
| securityevent/domain | VideoLinkResult, SecurityEventRepository.linkVideo |
| securityevent/infrastructure/persistence | SpringDataSecurityEventRepository, SecurityEventRepositoryAdapter — 잠금·조건부 UPDATE·NULL 덮어쓰기 방지 |

`application.yml`에 복구 설정을 추가했다. S3 어댑터, video-url API, Vision/Agent UseCase, FCM/경고음, 기존 migration, MP4 fallback 구현은 변경하지 않았다.

## 검증 범위

- StreamingAgentIntegrationTest: AUDIO/KEYPAD 연결, 양쪽 도착 순서, COMPLETED, 감지 없는 KEYPAD, 같은 원본 재전송, 일시적 실패, 과거 누락 복구, 충돌 보존, 잘못된 이벤트 연결 차단.
- 동일 테스트: 실제 Agent UseCase의 외부 호출을 latch로 멈춘 동안 업로드 후 저장, 오래된 관리 엔티티의 저장, 중복 동시 연결, 기존 HTTP video-url의 소유권 확인과 객체 키 전달.
- ProdDatabaseIntegrationTest: 일회용 실제 MySQL에서 migration/매핑 검증과 오래된 관리 엔티티 저장·동시 재시도·다른 경로 충돌 검증. S3 호출 없이 테스트 저장 메타데이터를 사용한다.
- 기존 업로드 API/영속성, LEGACY, fallback, Vision/Agent/알림/경고음 회귀 테스트.

실제 AWS/S3나 운영 Vision/Agent/FCM은 호출하지 않는다. 테스트의 S3 및 외부 서비스는 모킹한다.

## 실행 결과 (2026-11-04)

- 일회용 로컬 MySQL 8.0.43을 사용한 `./gradlew.bat build`: BUILD SUCCESSFUL, 52초.
- 총 394개: 392개 통과, 실패/오류 0개, 기존 2개 건너뛰기(비활성 BackendApplicationTests, MQTT 접속 환경 변수가 필요한 MqttAuthenticationIntegrationTest).
- StreamingAgentIntegrationTest 35개와 실제 MySQL ProdDatabaseIntegrationTest 9개 모두 통과.
- MySQL의 기존 migration V1~V11과 Hibernate 매핑 검증 통과. 새 migration이나 운영 DB 직접 수정은 없다.
- `git diff --check` 통과. 테스트 HTML 보고서: `build/reports/tests/test/index.html`.
