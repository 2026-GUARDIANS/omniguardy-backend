# EC2 V6~V11 Streaming 초기 활성화

이번 변경은 설정·전달 경로와 기존 worker 동작을 점검한 것이다. Docker 이미지 빌드/pull/up, AWS 변경, 운영 DB 조회, 운영 Vision/Agent/장치 요청은 실행하지 않는다. 실제 비밀값을 작성하지 않는다.

## 운영 값 전달

`deploy/.env.prod.example`을 참고해 EC2의 `deploy/.env.prod`에 운영 값을 주입한다. Compose 실행 시 기존처럼 `--env-file .env.prod`를 지정해야 Compose 보간과 컨테이너 `env_file`에 같은 파일이 사용된다. 파일을 Git에 추가하지 않는다.

```dotenv
STREAMING_VISION_POLLING_ENABLED=true
STREAMING_AGENT_ENABLED=true
VISION_FALLBACK_ENABLED=true
STREAMING_VISION_BASE_URL=
VISION_CALLBACK_API_KEY=
```

빈 두 항목은 배포 환경에서 채운다. 실제 키를 예제·문서·명령줄 로그에 넣지 않는다.

| 변수 | Spring 속성 | 전달/실행 조건 |
|---|---|---|
| STREAMING_VISION_BASE_URL | ai.fastapi.streaming-vision.base-url | Compose 필수. 컨테이너에서 접근 가능한 Streaming origin. `/vision` 경로를 붙이지 않음 |
| VISION_CALLBACK_API_KEY | omniguardy.vision.callback.api-key | Compose 필수. Vision의 `X-Vision-Callback-Key`와 일치, Device/JWT 키와 분리 |
| STREAMING_VISION_POLLING_ENABLED | ai.fastapi.streaming-vision.polling-enabled | Compose 기본 true, 명시적 false는 유지 |
| STREAMING_AGENT_ENABLED | omniguardy.streaming-agent.enabled | Compose 기본 true. 기존 `AGENT_AI_ENABLED=true`도 충족해야 V9 scheduler 실행 |
| VISION_FALLBACK_ENABLED | omniguardy.vision-fallback.enabled | Compose 기본 true. V11 worker 실행 |

Compose의 `env_file`은 기존 및 신규 tuning 변수 전체를 전달한다. 위 다섯 핵심 값만 `environment`에도 명시하여 워커 활성화 기본값과 URL/키 누락 검사를 고정했다. Compose 보간에서는 실행 셸 환경값이 `--env-file`보다 우선하므로 운영 셸에 남은 예전 false/URL/키도 확인한다. `AGENT_AI_ENABLED`는 새로 덮어쓰지 않고 기존 설정을 유지한다.

이 Compose에는 `vision-streaming` 서비스가 없다. 따라서 과거 예제의 `http://vision-streaming:8010`을 기본 주소로 사용하지 않는다. `localhost`는 EC2 호스트나 별도 Vision 서버가 아니라 backend 컨테이너 자신이다. 운영 서버의 접근 가능한 주소를 입력한다. LEGACY/MP4 fallback 주소인 `AI_FASTAPI_VISION_BASE_URL`과 Streaming 주소를 구분한다. Callback은 Vision → Spring HTTPS 경로 `/api/internal/ai/vision/early-events`로 도착해야 한다.

LEGACY, MySQL, MQTT, S3/IAM, Firebase 마운트, JWT, Agent/OpenAI 환경값은 기존 전달 방식을 유지한다. 새로운 secret 값, 이미지 태그, 서비스, 볼륨을 추가하지 않았다. `application.yml`의 `${...}` 매핑은 이미 올바르므로 동작을 바꾸지 않고 운영 주입·기존 작업 재개 설명을 추가했다.

## 자동 워커를 켰을 때의 기존 작업

**신규 세션만 처리하는 시작 시각 제한은 없다.** 기존 데이터가 있는 DB라면 첫 실행부터 미처리 작업을 발견·재개하고 조건에 따라 실제 Agent·알림·경고음을 실행할 수 있다. 현재 운영 DB에 접속하지 않았으므로 대상 건수는 확인하지 않았다. 새 빈 DB에는 해당 backlog가 없다.

| 기존 데이터 상태 | 활성화 후 동작 |
|---|---|
| V6 등록만 있고 V8 촬영 종료 작업 없음 | 자동 polling 시작 안 함 |
| V7 EARLY_EVENT만 수신 | Agent/fallback 직접 실행 안 함 |
| V8 PENDING / 만료 RUNNING | claim 후 polling 재개. 기존 deadline 만료 시 TIMEOUT/FALLBACK_REQUIRED로 전환 가능 |
| V8 유효한 VALID 결과, V9 이력 없음 | V9가 새 작업을 생성하고 조건 충족 시 Agent·대응 실행 |
| V9 PENDING / 만료 PREPARING | 준비 단계 재개, 기존 횟수 한도 적용 |
| V9 CALLING 기한 만료 / UNCERTAIN | 저장된 판단 확인 후 SUCCEEDED 또는 UNCERTAIN 정리. 불명확한 호출·알림은 재발행하지 않음 |
| V8 FALLBACK_REQUIRED / PARTIAL·FAILED·REJECTED | V11 이력 없으면 fallback 후보 발견. 과거 실패도 대상 |
| V9 BLOCKED + fallback_required=true | Agent 시도 0회·eventId 미연결인 입력 매핑 실패만 fallback 후보 |
| V9 Agent 실패/네트워크 오류/UNCERTAIN | Vision 실패로 간주해 fallback하지 않음. 명시적인 기존 V9 재시도 가능 실패는 기존 한도 내 재시도 |
| V11 WAITING_VIDEO / PENDING / 만료 RUNNING·PREPARED | 저장 원본 대기 또는 준비 복구. 기존 Vision 캐시·eventId 재사용 |
| V11 CALLING 기한 만료 | 이미 저장된 판단 확인 또는 UNCERTAIN. Agent 자동 재호출 없음 |
| V9/V11 SUCCEEDED·SKIPPED·종료 FAILED | 단순 활성화로 초기화·재처리하지 않음 |
| V10 UPLOADING / FAILED | worker가 파일 업로드를 대신 복구하지 않음. Pi의 동일 원본 재전송 필요 |

V11 영상 대기 기한은 **fallback 작업을 처음 발견한 시각**부터 기본 10분이다. 오래된 V8 실패를 지금 처음 발견해도 새 대기 기한을 갖는다. 반대로 기존 WAITING_VIDEO의 기한은 재시작으로 연장하지 않는다. V8 polling 기한과 Vision 서버 TTL 역시 배포로 초기화되지 않는다.

`VISION_FALLBACK_ENABLED=true`는 `AGENT_AI_ENABLED`에 종속된 조건이 아니다. 기존 Agent 설정이 false이면 V9 scheduler는 꺼지지만 V11 원본 재분석은 수행할 수 있고 Agent 단계는 disabled 오류로 끝날 수 있다. 전체 자동 판단을 원하는 이번 운영에서는 기존 Agent 활성화와 자격 증명이 유효한지 확인해야 한다. 이번 변경은 그 값을 강제로 true로 바꾸지 않는다.

## 중복 실행 점검 결과

- V8/V9/V11은 세션 PK, DB claim, lease/token으로 동시 실행과 stale worker를 제어한다. V9와 V11은 같은 VisionSession 잠금으로 소유권을 조정한다.
- 유효한 Streaming Agent 판단이 진행·완료된 세션은 fallback이 가져가지 못한다. V8/V9 원본 이력을 덮어쓰지 않는다.
- V11의 `security_agent_executions`는 LEGACY/V9/fallback이 같은 SecurityEvent의 Agent 경계를 동시에 실행하지 못하게 한다. 정상 완료된 판단은 결과만 재사용한다.
- RESERVED/CALLING/UNCERTAIN을 단순 시간 경과나 재시작만으로 해제하지 않는다. 불명확한 Agent/FCM/MQTT 결과는 자동 재생하지 않는다.
- 기존 LEGACY 분석 결과가 이미 이벤트에 반영되어 있으면 fallback은 SKIPPED로 처리한다. LEGACY AUDIO의 Agent 소유권 충돌은 409이며 그 충돌로 다른 작업자의 이벤트를 FAILED로 바꾸지 않는다.
- 외부 LLM/FCM/MQTT와 DB 사이의 exactly-once 전달까지 보장하는 구조는 아니다. 기존 best-effort 대응을 유지하며, 알림 전달이 불명확할 때 재발행하지 않는 쪽으로 처리한다.
- 공통 guard가 없는 구버전 인스턴스와 새 버전을 동시에 실행하면 전체 경로의 중복 방지를 보장할 수 없다. 초기 빈 환경은 V11이 포함된 새 버전만 시작한다. 기존 환경 교체는 구버전 요청/worker를 종료한 뒤 새 버전을 시작해야 한다.

DB를 백업·복제한 검토 환경에서 [streaming-worker-audit.sql](streaming-worker-audit.sql)을 실행하면 V8~V11 상태별 건수, 오래된 polling, V9 신규 후보, 영상 도착 여부별 fallback 후보, 불명확한 Agent 이력을 읽기 전용으로 확인할 수 있다. V11 스키마가 필요하며, 이 파일을 여기서 운영 DB에 실행하지 않았다. 활성 worker와 동시에 조회한 결과는 움직이는 시점의 현황이다.

현재 활성화 계획은 유지한다. 기존 데이터가 있다면 발견될 과거 작업이 의도한 대상인지 위 조회로 확인한 뒤 같은 활성화 설정으로 배포한다. 원치 않는 테스트/과거 작업을 처리 대상에서 제외해야 할 경우에는 개별 이력을 검토해야 한다. 일괄 DELETE, 상태 PENDING 초기화, lease 강제 해제 SQL은 제공·실행하지 않는다. `video_uploads.running=true`인 기존 LEGACY 이력도 Streaming worker가 대신 해제하지 않는다.

## 배포 전 설정 확인 방법

EC2에서 실제 값을 안전하게 주입한 뒤 다음 **구성 검증만** 수행할 수 있다.

```bash
cd /opt/omniguardy/deploy
docker compose --env-file .env.prod -f docker-compose.prod.yml config --quiet
```

이는 이미지 빌드나 서비스 시작 명령이 아니다. URL/키가 비어 있으면 Compose 단계에서 실패한다. 전체 `config` 출력에는 비밀값이 들어갈 수 있으므로 출력·공유하지 않는다. `--quiet` 성공은 네트워크 연결이나 실제 인증의 성공을 뜻하지 않는다.

prod는 기존대로 Flyway를 실행하고 Hibernate validate를 적용한다. V6~V11과 guard를 포함한 애플리케이션을 사용한다. 이번 설정 변경은 migration 또는 DB 데이터를 수정하지 않는다. scheduler 활성화 자체는 운영 반영 전까지 실행되지 않는다.

## 이번 검증

2026-11-02 검증 결과:

- 합성 값만 사용해 실제 `deploy/docker-compose.prod.yml`의 `docker compose config --format json`을 8개 시나리오로 검증했다. 실제 `.env.prod`는 읽거나 수정하지 않았다.
- 초기 true 전달, 명시적 false 유지, 플래그 생략 시 true 기본값, Streaming URL/Callback 키의 빈 값·누락 거부, 개별 tuning 값 전달을 확인했다. 기존 DB/MQTT/JWT/S3/Agent/LEGACY 값, Firebase 경로, backend의 MySQL root 키 제외도 확인했다.
- polling 17개 + V9 Agent 24개 + fallback 24개 = **65개 테스트 통과, 실패/오류/건너뛰기 0개**, Gradle test 1분 7초. 외부 AI/S3/FCM/경고음은 Mock, 저장 이력은 H2를 사용했다.
- `git diff --check` 통과. 점검 SQL은 읽기 전용으로 작성했으며 운영 DB에는 실행하지 않았다.
- Docker 이미지 build/pull/up, 실제 배포, 운영 비밀값 작성·운영 서버 접속은 수행하지 않았다.

이번 변경 파일: `deploy/docker-compose.prod.yml`, `deploy/.env.prod.example`, `src/main/resources/application.yml`(기존 매핑에 설명 추가), `deploy/README.md`, `deploy/STREAMING.md`, `deploy/streaming-worker-audit.sql`. 기존 Java 처리 로직과 migration은 이번 설정 작업에서 수정하지 않았다.
