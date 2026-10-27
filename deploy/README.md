# OmniGuardy EC2 배포 준비

이 디렉터리는 배포용 설정과 실행 안내입니다. AWS 리소스 생성, 실제 배포, Docker Hub 업로드는 수행하지 않았습니다. 실제 비밀값은 예제에 없습니다. 검증 결과는 [VALIDATION.md](VALIDATION.md), 파일별 변경 목록은 [CHANGES.md](CHANGES.md)를 참조하세요.

## 배포 전 우선순위

1. **P0**: 운영 도메인/DNS·HTTPS, Firebase JSON, DB/JWT/MQTT/장치 키, Audio/Vision ngrok HTTPS 주소, S3 버킷과 EC2 IAM Role을 준비합니다. IAM·인증·실제 AI 응답은 로컬 Mock 테스트만으로 보장되지 않습니다.
2. **P0**: 새 DB는 Flyway V1~V5로 시작합니다. 기존 DB를 가져올 때에는 아래 마이그레이션 절차를 먼저 수행해야 합니다. `ddl-auto=validate`를 유지합니다.
3. **P0**: 앱 회원가입으로 실제 사용자를 만든 다음 `OMNIGUARDY_DEMO_USERID`를 해당 ID로 지정합니다. 현재 Pi 이벤트는 이 사용자의 이벤트로 귀속됩니다. 다중 가정/다중 사용자 장치 소유권 모델은 이번 범위에 포함되지 않습니다.
4. **P0**: MQTT SSH 터널/VPN을 Pi에 구성합니다. 1883은 보안 그룹에서 열지 않습니다. 기존 `/api/edge/**`, `/api/keypad/**`, `/api/sounds/**`의 익명 접근 계약은 유지되므로 인터넷 공개 시 Nginx 접근 제한/VPN 또는 향후 별도 장치 인증이 필요합니다. `AUDIO_DEVICE_API_KEY`는 `/api/internal/ai/audio-result`만 보호합니다.
5. **P1**: 메모리·디스크·업로드 시간·FCM 푸시·Pi 실제 촬영/사운드를 스테이징에서 측정합니다. 1GiB 메모리에서 세 서비스와 OS/Nginx가 여유롭게 실행된다고 보장하지 않습니다. 메모리가 작은 호스트는 암호화된 디스크의 swap과 모니터링이 필요하며, 가능하면 2GiB 이상을 사용합니다. 프리티어 적용 여부는 해당 AWS 계정에서 확인합니다.
6. **P1**: MySQL 백업/복구, S3 보관 기간, 실패 업로드 이력 점검, 인증서 갱신, 고정 이미지 태그와 롤백 절차를 준비합니다.

## 구성과 메모리

- Ubuntu 계열 EC2를 전제로 Docker Engine, Compose v2 플러그인, 호스트 Nginx/Certbot을 설치합니다. CPU 아키텍처와 Docker 이미지 플랫폼을 일치시킵니다. 여기서는 EC2에서 Gradle 빌드하지 않고 개발 PC/CI에서 빌드한 이미지를 받습니다.
- backend는 비root UID/GID `10001`, 메모리 제한 512MiB, Java heap 256MiB, metaspace 128MiB입니다. MySQL은 384MiB/버퍼 풀 128MiB/최대 연결 20, MQTT는 64MiB입니다. 제한 합계와 별도로 OS/Nginx/파일 캐시 메모리가 필요합니다. swap 설정은 파일에 자동 적용하지 않았습니다. `memswap_limit`은 호스트 swap이 존재해야 의미가 있습니다.
- backend만 `127.0.0.1:8080`에 바인딩합니다. MySQL은 호스트 포트가 없습니다. MQTT는 `127.0.0.1:1883`만 바인딩합니다. 보안 그룹은 API용 80/443, 관리자/터널용 22를 허용된 IP 범위로 제한합니다. 3306/1883은 인터넷에 공개하지 않습니다.
- MySQL, 로컬 오디오, MQTT 데이터만 named volume으로 유지합니다. 영상 영구 볼륨과 Google TTS 볼륨은 없습니다. 업로드 임시 파일은 컨테이너 디스크 `/tmp`를 사용하며 메모리 tmpfs를 사용하지 않습니다.
- 영상 API 자체 제한은 **100MiB**입니다. Multipart 요청은 105MiB, Nginx도 105MiB를 허용합니다. 개발 YAML의 500MB는 운영 영상 API 제한을 올리지 않습니다. Nginx·서블릿·컨트롤러의 디스크 버퍼가 동시에 필요할 수 있으므로 여유 디스크를 확보합니다.
- Nginx는 동시에 처리하는 영상 요청을 1개로 제한하고 초과 시 429를 반환합니다. backend 헬스체크 `/hello`는 HTTP 생존 확인이며 S3/FCM/AI 정상 여부는 보장하지 않습니다. MQTT 헬스체크도 TCP 리스너 확인이며 ACL 검증은 별도입니다.

## 환경변수

EC2에서 `deploy/.env.prod.example`을 `.env.prod`로 복사해 작성합니다. 개발 루트 `.env`는 유지되며 dev에서만 import합니다. 운영 컨테이너에는 Compose `env_file`이 `.env.prod`를 전달하고 prod 프로필을 강제합니다. YAML은 나열한 이름을 명시적으로 매핑하므로 하이픈 변환 이름에 의존하지 않습니다.

`P` = `src/main/resources/application-prod.yml`, 경로는 `src/main/java/com/omniguardy/backend` 기준입니다. 기본값이 있는 변수는 필요할 때 변경합니다. 예제에 빈 값인 필수 항목은 반드시 직접 채웁니다. 값에 `$`, `#`, 공백이 있으면 Compose `.env` 문법에 맞게 단일 따옴표로 감쌉니다. 비밀값이 출력되는 `docker compose config` 대신 `config --quiet`를 사용합니다.

| 변수 | 설정 속성 / 사용 코드 | 필수·기본값·누락 영향 |
|---|---|---|
| BACKEND_IMAGE | Compose backend.image | 직접 입력 필수. Docker Hub 계정/이미지:고정태그. 누락 시 Compose 실패 |
| SPRING_PROFILES_ACTIVE | Spring 프로필 | Compose에서 prod 강제 |
| MYSQL_DATABASE | Compose mysql | 기본 omniguardy_db. DB_URL DB명과 일치 |
| DB_URL | P → spring.datasource.url | 필수. Compose 서비스명 mysql:3306. 누락/오류 시 기동 실패 |
| DB_USERNAME | P → spring.datasource.username / Compose MYSQL_USER | 필수. root 이외 앱 계정 |
| DB_PASSWORD | P → spring.datasource.password / Compose MYSQL_PASSWORD | 직접 입력 필수. 비면 Compose/앱 실패 |
| MYSQL_ROOT_PASSWORD | Compose mysql | 직접 입력 필수. backend에는 빈 값으로 덮어씀 |
| JWT_SECRET_KEY | P → jwt.secret-key / domain/auth/infrastructure/security/JwtProvider | 직접 입력 필수. 무작위 32바이트 이상. 누락·짧은 값은 기동 실패 |
| JWT_ACCESS_TOKEN_EXPIRE_TIME | P → jwt.access-token-expire-time / JwtProvider | 선택. 기본 1800000ms |
| JWT_REFRESH_TOKEN_EXPIRE_TIME | P → jwt.refresh-token-expire-time / JwtProvider | 선택. 기본 1209600000ms |
| MQTT_BROKER_URL | P → mqtt.broker-url / global/mqtt/MqttConfig | 기본 tcp://mosquitto:1883. 내부 Docker 네트워크 |
| MQTT_CLIENT_ID | P → mqtt.client-id / MqttConfig | 기본 omniguardy-prod. 여러 backend이면 고유값 필요 |
| MQTT_USERNAME | P → mqtt.username / MqttConfig | 필수. 기본 예제 backend. password 파일·ACL과 일치 |
| MQTT_PASSWORD | P → mqtt.password / MqttConfig | 직접 입력 필수. 브로커 password 파일과 일치. 불일치 시 발행 실패 |
| MQTT_TOPIC_CAMERA | P → mqtt.topic.camera / domain/detection/infrastructure/mqtt/MqttCameraCommandAdapter | 기본 door/camera. 바꾸면 ACL/Pi도 변경 |
| AUDIO_DEVICE_API_KEY | P → omniguardy.device.api-key / global/security/auth/AudioDeviceAuthenticationFilter | 직접 입력 필수. 송신 측 X-Device-Api-Key와 동일. 불일치 시 콜백 401 |
| OMNIGUARDY_DEMO_USERID | P → omniguardy.demo.user-id / domain/user/application/usecase/DemoUserResolver, JdbcVideoUploadJournal | 직접 입력 필수. 양의 정수·실제 users 레코드. 사용자가 없으면 이벤트/영상 처리 실패 |
| AI_FASTAPI_AUDIO_BASE_URL | P → ai.fastapi.audio.base-url / AudioFastApiAdapter, FastApiMediaAnalysisAdapter | 직접 입력 필수. 팀원 Audio ngrok HTTPS origin |
| AI_FASTAPI_AUDIO_ENDPOINT | P → ai.fastapi.audio.endpoint / 동일 | 기본 /predict |
| AI_FASTAPI_VISION_BASE_URL | P → ai.fastapi.vision.base-url / VisionFastApiAdapter, FastApiMediaAnalysisAdapter | 직접 입력 필수. 팀원 Vision ngrok HTTPS origin |
| AI_FASTAPI_VISION_ENDPOINT | P → ai.fastapi.vision.endpoint / 동일 | 기본 /analyze/vision |
| AUDIO_CONNECT_TIMEOUT_SECONDS | P → ai.fastapi.audio.connect-timeout-seconds / AudioFastApiAdapter | 기본 10초 |
| AUDIO_READ_TIMEOUT_SECONDS | P → ai.fastapi.audio.read-timeout-seconds / AudioFastApiAdapter | 기본 60초 |
| VISION_CONNECT_TIMEOUT_SECONDS | P → ai.fastapi.vision.connect-timeout-seconds / VisionFastApiAdapter | 기본 10초 |
| VISION_READ_TIMEOUT_SECONDS | P → ai.fastapi.vision.read-timeout-seconds / VisionFastApiAdapter | 기본 180초. ngrok/실제 영상 처리 시간 측정 후 조정 |
| AGENT_AI_ENABLED | P → ai.agent.enabled / domain/securityevent/infrastructure/agent/OpenAiAgentConfig | 기본 true. false이면 기존 AGENT_AI_DISABLED 동작 유지 |
| OPENAI_API_KEY | P → ai.agent.openai.api-key / OpenAiAgentConfig | AI 활성화 시 직접 입력 필수. 비면 기동 실패 |
| OPENAI_MODEL_NAME | P → ai.agent.openai.model-name / 동일 | 기본 gpt-5-mini |
| OPENAI_TIMEOUT_SECONDS | P → ai.agent.openai.timeout-seconds / 동일 | 기본 60초. 기존 SDK 재시도 정책 유지 |
| AWS_REGION | P → aws.region / domain/detection/infrastructure/storage/S3StorageConfig | 필수. 예제 ap-northeast-2. 버킷 리전과 일치 |
| S3_BUCKET_NAME | P → aws.s3.bucket / S3MediaStorageAdapter, S3VideoUrlAdapter | 직접 입력 필수. 미생성/권한 오류는 실제 업로드 시 실패 |
| S3_VIDEO_PREFIX | P → aws.s3.video-prefix / 동일 | 기본 videos. IAM/Lifecycle prefix도 함께 변경 |
| FIREBASE_CREDENTIALS_HOST_PATH | Compose 읽기 전용 bind source | 직접 운영 경로 확인. 파일이 없으면 컨테이너 생성 실패 |
| FIREBASE_CREDENTIALS_PATH | P → firebase.credentials-path / global/config/FirebaseConfig | Compose가 /run/secrets/firebase-service-account.json으로 강제. 파일 누락/잘못된 JSON은 명확한 오류로 기동 실패 |
| CORS_ALLOWED_ORIGINS | P → app.security.cors-allowed-origins / global/config/SecurityConfig | 기본 빈 목록. 네이티브 RN은 CORS 불필요. 웹 화면이 있다면 정확한 HTTPS origin 목록을 쉼표로 지정 |
| COOKIE_SAME_SITE | P → app.security.cookie-same-site / AuthController → CookieUtil | 기본 Lax. 교차 사이트 웹 쿠키가 필요할 때만 None 검토. prod Secure=true 고정 |
| MULTIPART_MAX_FILE_SIZE | P → spring.servlet.multipart.max-file-size | 기본 100MB. API 자체 100MiB 제한 유지 |
| MULTIPART_MAX_REQUEST_SIZE | P → spring.servlet.multipart.max-request-size | 기본 105MB. Nginx 설정과 함께 관리 |
| JAVA_TOOL_OPTIONS | Dockerfile Java 실행 옵션 | 예제 env에는 없음. 메모리 측정 후에만 선택적으로 override |
| AWS_EC2_METADATA_V1_DISABLED | Compose / AWS SDK | true 강제. EC2에서 IMDSv2 사용 |

`MQTT_TOPIC_TTS`, `APP_BASE_URL`, `GOOGLE_APPLICATION_CREDENTIALS`는 prod에서 사용하지 않아 추가하지 않았습니다. `door/tts`는 Google TTS를 켜는 dev에서만 사용합니다. `door/sound`는 기존 Java 상수와 사운드 경고/사이렌 payload를 유지했습니다. 별도 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`는 설정하지 않습니다.

## S3, IAM, 영상 접근

1. 같은 리전의 전용 비공개 버킷을 준비하고 Block Public Access 전체 활성화, Object Ownership bucket owner enforced, 기본 SSE-S3 암호화를 적용합니다. 버킷 정책에서 HTTPS가 아닌 요청을 거부합니다.
2. EC2 trust principal `ec2.amazonaws.com`의 IAM Role/instance profile을 사용합니다. [ec2-s3-policy.example.json](aws/ec2-s3-policy.example.json)의 버킷명/prefix를 바꾸어 검토합니다. 앱에는 해당 prefix의 PutObject/GetObject만 필요합니다. 객체 ACL 공개, DeleteObject, 전체 S3 권한은 필요하지 않습니다. SSE-KMS로 바꾸면 코드 암호화 설정과 KMS 권한을 별도로 변경해야 합니다.
3. SDK 2.x `DefaultCredentialsProvider`를 사용합니다. 환경변수/프로파일 자격증명은 Role보다 먼저 선택될 수 있으므로 키 환경변수와 `~/.aws` 마운트를 사용하지 않습니다. [AWS 기본 인증 체인](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html)
4. EC2 메타데이터 endpoint 활성화, tokens required(IMDSv2), Docker bridge를 위해 hop limit 2를 검토합니다. 컨테이너에서 `169.254.169.254` 접근을 막는 방화벽/프록시 설정도 확인합니다. 프록시가 있다면 해당 주소를 NO_PROXY에 포함합니다. Role의 실제 권한과 네트워크 접근은 EC2에서 확인해야 합니다. [AWS IMDS 컨테이너 안내](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-IMDS-new-instances.html)
5. DB `security_events.video_path`와 `video_uploads.object_key`에는 URL 대신 객체 키를 저장합니다. 기존 SecurityEvent의 영상 시간·분석 결과 필드를 재사용합니다. 정상 KEYPAD 영상도 `video_uploads`에 파일명/크기/타입/hash/사용자/분석/객체 키가 남습니다.
6. 추가한 `GET /api/security-events/{eventId}/video-url`은 JWT 인증 후 이벤트 소유자를 검증하여 `{ "url": "..." }` 형식의 **5분** Presigned URL을 반환합니다. 기존 API 응답은 유지합니다. URL은 보유자가 만료 전 사용할 수 있으므로 로그/공유/장기 캐시에 저장하지 않습니다. 정상 KEYPAD처럼 이벤트가 없는 영상의 조회 API는 추가하지 않았습니다.
7. [s3-lifecycle.example.json](aws/s3-lifecycle.example.json)은 30일 보관 예제입니다. 팀의 보관 정책을 확정한 뒤 적용하며, 이번에 AWS에 적용하지 않았습니다. 수명 만료 뒤에도 DB 메타데이터는 남아 URL 접근이 404가 될 수 있습니다. 장기 보관이 필요한 사건은 별도 보관 정책을 정해야 합니다. 버전 관리 사용 시 이전 버전 삭제도 고려합니다.

## 영상 흐름, 재시도와 실패 복구

`/api/edge/video`의 file/eventId/triggerId/triggerType/securityEventId/triggeredAt 계약과 Vision Multipart 필드는 유지합니다. 이전 AUDIO 요청의 eventId fallback과 기존 이벤트 연결 규칙도 유지합니다.

1. 크기를 검사하고 요청 파일을 임시 디스크 파일로 옮깁니다. 영상은 byte[] 전체 복사 대신 스트림으로 Vision과 S3에 전달합니다. 오디오의 기존 로컬 저장 방식은 유지합니다.
2. prod에서 `video_uploads`에 트리거별 처리 이력을 생성하고 원자적으로 claim합니다. 트리거는 AUDIO의 securityEventId(없으면 eventId), KEYPAD의 triggerId로 식별합니다. 동일 트리거에 다른 내용(hash), KEYPAD 촬영 시각 변경, 동시 실행은 409입니다. Pi는 재시도 시 같은 영상/트리거/시각을 유지해야 합니다.
3. Vision 성공·계약 검증 후 분석 결과를 DB에 checkpoint합니다. Vision 실패 시 S3에는 저장하지 않습니다. AUDIO 이벤트는 기존대로 FAILED가 됩니다. Vision 완료와 checkpoint 사이에서 장애가 나면 해당 외부 호출이 다시 필요할 수 있습니다.
4. S3 키는 트리거 ID와 내용 SHA-256으로 결정합니다. `If-None-Match: *` 조건부 PUT으로 같은 키를 재사용합니다. 이미 존재하는 객체의 412는 성공으로 취급합니다. S3 실패 시 분석 checkpoint를 유지하므로 재시도에 Vision을 다시 호출하지 않습니다.
5. S3 성공 후 객체 키를 checkpoint하고 기존 SecurityEvent 저장/Agent 분석을 수행합니다. KEYPAD 이벤트 ID는 triggerId 기반의 안정적인 UUID로 만들어 DB 커밋 직후 응답이 유실되어도 중복 이벤트를 만들지 않습니다. 정상 KEYPAD는 기존대로 SecurityEvent를 만들지 않습니다.
6. DB 실패 시 S3 객체를 무조건 삭제하지 않습니다. DB 커밋 성공 여부가 불명확할 수 있기 때문입니다. 재시도 시 동일 키를 사용하고 이력에 연결합니다. S3 성공 직후 DB checkpoint가 실패해도 재시도의 조건부 PUT은 추가 객체/버전을 만들지 않습니다. 미완료 이력과 Lifecycle을 함께 점검합니다. S3와 MySQL 사이의 원자적 분산 트랜잭션을 보장하지는 않습니다.
7. Agent 실패 시 영상/분석 checkpoint를 재사용하고 해당 Agent 단계만 다시 실행합니다. 이미 COMPLETED인 이벤트는 Agent/푸시/사운드를 재실행하지 않습니다. 외부 알림과 DB 사이의 완전한 exactly-once는 보장하지 않으며, 이번에 Outbox를 추가하지 않았습니다.
8. 완료 응답도 저장해 동일 요청의 재전송에는 기존 응답을 반환합니다. 임시 파일은 finally에서 삭제합니다. 강제 종료로 남은 임시 파일은 컨테이너 교체 시 없어지며, 같은 컨테이너를 오래 재사용하면 디스크를 점검합니다.

**강제 종료 복구:** `running=true` claim은 자동 만료시키지 않습니다. 오래 걸리는 요청과 새 요청이 겹쳐 중복 실행되는 것을 방지하기 위한 선택입니다. backend를 정지하고 진행 중 요청이 없음을 확인한 뒤, DB 백업과 S3/이력 확인을 거쳐 해당 이력 ID에 한해서 `UPDATE video_uploads SET running = FALSE WHERE id = '<확인한 ID>';`로 복구할 수 있습니다. 전체 이력 일괄 초기화, 임의 객체 삭제, 실행 중 잠금 해제는 하지 않습니다. 이 문서의 SQL은 실행하지 않았습니다.

## MySQL / Flyway

- 기존 V1/V2는 테이블이 없으면 no-op입니다. V3는 오디오 수신 이력 테이블을 만듭니다. 기존 파일·checksum은 수정하지 않았습니다.
- V4는 빠진 기본 테이블만 `CREATE TABLE IF NOT EXISTS`로 생성합니다. V5는 영상 이력 테이블을 추가합니다. prod는 baseline-on-migrate=false, ddl-auto=validate입니다. Flyway 자동 설정을 위해 Spring Boot 4용 starter를 사용합니다. [Spring Boot 4 변경 안내](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- **빈 새 DB:** V1→V5를 실행한 후 Hibernate validate로 검사합니다. 앱 사용자는 자동 생성되지 않습니다. 회원가입 후 실제 사용자 ID를 지정합니다.
- **기존 Flyway 이력이 있는 DB:** 백업과 복제 DB에서 이력·스키마를 확인하고 V4/V5만 추가 적용합니다. V4는 기존 테이블의 누락 컬럼을 자동 보정하지 않으므로 validate 실패 시 별도 검토한 새 마이그레이션이 필요합니다.
- **기존 이력이 없는 DB:** 자동 baseline을 켜지 않습니다. V1~V3가 이미 반영되어 있는지 확인한 뒤, 검토된 정확한 버전으로 명시적 Flyway baseline을 수행하는 별도 작업이 필요합니다. 예를 들어 모든 V1~V3 구조가 실제 존재할 때만 baseline 3을 검토합니다. 확인 없이 baseline하거나 V3를 다시 실행하면 기존 테이블 충돌이 납니다.
- dev/test는 기존 Hibernate update/create-drop 동작을 유지하도록 Flyway 자동 실행을 끕니다. 개발 `.env` import만 dev 파일로 이동했으며 기존 MQTT 주소 등 사용자 변경은 유지했습니다.
- 기존 코드에서는 Domain Model이 JPA Entity도 겸합니다. 이번에 전체 모델을 분리하지 않고 기존 구조를 유지했습니다. 새 외부 연동은 Application Port → Infrastructure Adapter이며 새 영상 이력은 JDBC 구현이므로 Domain에 S3 SDK를 추가하지 않았습니다.

## Firebase 파일과 MQTT 준비 (EC2에서 직접 실행할 안내)

다음 명령은 실제 서버에서 검토 후 실행할 예시이며 여기서는 실행하지 않았습니다.

```bash
cd /opt/omniguardy/deploy
umask 077
cp .env.prod.example .env.prod
chmod 600 .env.prod
mkdir -p secrets
# 편집기로 .env.prod의 필수 빈 값을 채웁니다. 값을 터미널에 출력하지 않습니다.
```

Firebase JSON을 `FIREBASE_CREDENTIALS_HOST_PATH`에 안전하게 복사합니다. 호스트에서 UID/GID 10001이 읽도록 소유권/권한을 부여합니다(예: root:10001, 파일 0640). 부모 디렉터리에는 필요한 통과 권한만 줍니다. Compose는 JSON을 읽기 전용으로 마운트합니다. 이미지에는 `.env`, Firebase JSON, Google 키, 고정 개발 비밀값이 있는 dev/test YAML이 들어가지 않습니다. dev의 클래스패스 Firebase fallback은 유지되지만, prod에서는 외부 경로가 필수입니다. Google TTS 인증/마운트는 필요 없습니다.

MQTT password 파일은 해시 형식입니다. 다음은 실제 비밀번호를 명령행 인수/셸 히스토리에 넣지 않는 대화식 생성 예시입니다. 사용자명은 ACL과 일치시킵니다.

```bash
docker run --rm -it --user "$(id -u):$(id -g)" \
  -v "$PWD/secrets:/work" eclipse-mosquitto:2.0 \
  mosquitto_passwd -c /work/mosquitto.passwd backend
docker run --rm -it --user "$(id -u):$(id -g)" \
  -v "$PWD/secrets:/work" eclipse-mosquitto:2.0 \
  mosquitto_passwd /work/mosquitto.passwd raspberrypi
# 브로커 UID/GID가 읽을 수 있도록 소유권을 확인합니다(공식 이미지의 mosquitto 계정).
sudo chown 1883:1883 secrets/mosquitto.passwd
sudo chmod 640 secrets/mosquitto.passwd
```

backend 비밀번호는 `.env.prod`의 MQTT_PASSWORD와 일치해야 합니다. Pi에는 raspberrypi 비밀번호만 전달합니다. backend는 door/camera·door/sound 발행, Pi는 해당 토픽 구독만 허용합니다. 다른 실제 도어락 토픽이 Pi 코드에 있다면 그 토픽을 확인한 뒤 ACL에 최소 권한으로 추가합니다. 저장소에는 별도 도어락 MQTT 토픽 구현이 확인되지 않았으므로 임의 토픽 권한은 추가하지 않았습니다.

Pi에서 SSH 터널 예시:

```bash
ssh -N -L 127.0.0.1:1883:127.0.0.1:1883 \
  -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 \
  ubuntu@YOUR_EC2_HOST
```

Pi MQTT 클라이언트는 `127.0.0.1:1883` + raspberrypi 계정으로 접속하고 터널을 systemd/autossh 등으로 유지합니다. EC2 SSH host key를 검증하고 터널 계정의 포워딩 목적지를 제한합니다. Pi가 이미 로컬 1883을 사용하면 로컬 포트를 바꿉니다. VPN 대안은 VPN 인터페이스에만 listener를 바인딩하고 VPN CIDR만 허용합니다. 직접 TLS 브로커를 공개하려면 별도 8883 listener와 인증서/갱신/CA 검증을 준비해야 하며, 현재 Compose는 이를 자동 구성하지 않습니다.

## 이미지와 Compose 실행 안내

개발 PC/CI에서 서버 플랫폼에 맞는 이미지를 빌드합니다. 예시는 amd64입니다. ARM EC2이면 플랫폼도 변경합니다.

```bash
./gradlew build
docker build --platform linux/amd64 -t YOUR_DOCKERHUB/omniguardy-backend:VERSION .
docker login
docker push YOUR_DOCKERHUB/omniguardy-backend:VERSION
```

EC2에 deploy 디렉터리를 전달하고 `.env.prod`의 BACKEND_IMAGE를 위 태그로 설정합니다. 실제 비밀파일은 따로 준비합니다.

```bash
cd /opt/omniguardy/deploy
docker compose --env-file .env.prod -f docker-compose.prod.yml config --quiet
docker compose --env-file .env.prod -f docker-compose.prod.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 backend
```

`env_file` 경로는 Compose 파일 기준입니다. 반드시 위 `--env-file .env.prod`도 지정해야 image/DB/mount 보간에 같은 파일을 사용합니다. 기존 named volume의 MySQL 비밀번호/DB는 env 변경만으로 바뀌지 않습니다. 비밀번호 회전은 DB 계정과 앱 환경변수를 함께 변경하는 별도 절차가 필요합니다. `down -v`는 운영 데이터 삭제 명령이므로 사용하지 않습니다. 롤백은 이전 이미지 태그를 지정하되 DB 마이그레이션 하위 호환성을 확인합니다.

## Nginx / HTTPS

1. 도메인 A 레코드를 EC2 주소에 연결합니다. 80/443 인바운드와 아웃바운드 HTTPS/DNS를 확인합니다.
2. 인증서가 아직 없을 때는 먼저 80번 ACME challenge용 서버만 설치합니다. 웹루트 `/var/www/letsencrypt`를 만들고 Certbot webroot로 도메인 인증서를 발급합니다. 인증서가 없는 상태로 예제 443 설정을 먼저 활성화하면 Nginx 검증이 실패합니다.
3. `nginx/omniguardy-proxy.conf`를 `/etc/nginx/snippets/omniguardy-proxy.conf`에 복사하고 `omniguardy.conf.example`의 도메인/인증서 경로를 바꾸어 sites-available에 설치·활성화합니다. `limit_conn_zone`은 http 컨텍스트에 있어야 합니다.
4. `sudo nginx -t` 성공 후 reload합니다. Certbot 자동 갱신 timer와 renew dry-run, 갱신 후 Nginx reload를 확인합니다. 실제 도메인/인증서가 없어 이 단계는 미검증입니다.
5. Nginx가 X-Forwarded-Proto/Host/Port/For를 직접 덮어쓰고 backend는 framework forwarding을 사용합니다. backend는 localhost로 제한합니다. prod Refresh Token 쿠키는 발급/삭제 모두 Secure=true, HttpOnly, 기본 SameSite=Lax입니다. JWT 생성·검증 방식은 유지합니다.

예제 timeout은 connect 10초/read 600초/send 180초입니다. read는 Vision+S3+Agent 동기 처리와 SDK 재시도 시간을 고려한 값이며 실제 영상으로 측정해 조정해야 합니다. RN/Pi의 HTTP timeout도 이 전체 처리 시간과 맞추고, 무조건 영상을 재촬영하지 말고 같은 트리거/파일로 제한적으로 재시도합니다. 429/409는 즉시 병렬 재전송하지 않습니다.

## 팀원에게 전달할 변경

- **React Native**: API base URL `https://YOUR_API_DOMAIN`. 기존 요청/응답·JWT 인증 유지. 네이티브 앱의 쿠키 저장/갱신 동작을 Android/iOS에서 실제 확인합니다. 브라우저/WebView가 있다면 origin과 SameSite 요구를 별도로 확인합니다. 영상 조회는 인증 후 신규 video-url API를 호출합니다.
- **Raspberry Pi**: HTTP(S) base URL을 동일 운영 주소로 변경하고 CA 인증서 검증을 유지합니다. 촬영/키패드/AUDIO·KEYPAD 필드와 MQTT payload는 그대로입니다. MQTT 접속만 SSH 터널/VPN 주소·계정으로 변경합니다. 익명 접속은 더 이상 허용되지 않습니다.
- **Audio/Vision 팀원**: FastAPI를 로컬에서 실행하고 각각 ngrok HTTPS 주소를 `.env.prod`에 입력합니다. base URL 끝 슬래시와 endpoint 선행 슬래시의 중복을 피합니다. ngrok 주소 변경 시 backend를 재생성합니다. 기존 file/trigger 필드와 응답 JSON 구조를 유지하며 ngrok-skip-browser-warning 헤더를 사용합니다. 오디오 결과 callback 서버 주소와 X-Device-Api-Key도 맞춥니다.
- **FCM 담당**: 운영 프로젝트 JSON을 외부 파일로 공급하고 앱 Firebase 프로젝트/device token과 일치시킵니다. 실제 푸시 성공 여부는 기기에서 별도로 확인합니다.
- **오디오 보관**: 기존 DB audio_path 참조를 유지하려고 audio-data 볼륨을 추가했습니다. 기존 저장 방식에는 자동 삭제가 없으므로 디스크 사용량·보관 기간을 정합니다. DB가 참조하는 파일을 무작정 삭제하지 않습니다.

## 이번 작업에서 하지 않은 것

AWS EC2/S3/IAM/DNS 변경, 이미지 push, 실제 서버 배포, 운영 DB 초기화·삭제, 실제 S3/AI/OpenAI/FCM 호출은 수행하지 않았습니다. 실제 인증정보를 출력하거나 Git에 추가하지 않았습니다. GitHub Actions는 후속 작업입니다.
