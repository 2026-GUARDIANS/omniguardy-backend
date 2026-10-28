# 로컬 검증 결과

검증일: 2026-10-27. 실제 AWS 자원 생성/수정, Docker Hub push, 서버 배포는 수행하지 않았습니다. 로컬 일회용 MySQL/MQTT와 Mock을 사용했으며 운영 DB에는 접속하지 않았습니다.

| 항목 | 결과 | 검증 범위와 한계 |
|---|---|---|
| Java/Gradle compile·build | 통과 | `gradlew.bat build` 최종 성공 |
| 전체 테스트 | 182개 중 181개 통과, 실패 0, 건너뜀 1 | 기존 BackendApplicationTests의 환경 의존 contextLoads는 원래 Disabled. 새 prod 통합 테스트로 별도 기동 검증 |
| 빈 MySQL 8.0.43 | 통과 | 일회용 새 컨테이너 DB에 Flyway V1~V5 성공, Hibernate ddl-auto=validate 성공 |
| 기존 데이터 유지 | 통과(제한된 사례) | 같은 일회용 DB에 생성한 테스트 사용자 레코드에 V4를 다시 적용해 행 유지 확인. 실제 기존 DB/과거 스키마 복사본은 미검증 |
| prod 기동·설정 매핑 | 통과(Mock 포함) | SpringBootTest prod context. DB/JWT/Audio/Vision/리전/S3 등 설정, S3 Adapter 선택, DisabledTtsAdapter 선택 확인. FirebaseConfig는 기동 테스트에서 Mock |
| 환경변수 목록 | 통과 | prod YAML의 33개 명시적 placeholder 모두 `.env.prod.example`에 존재. Compose용 변수와 고정 실행 설정은 추가로 문서화 |
| S3 업로드 단위 테스트 | 통과 | Mock S3Client로 디스크 스트리밍, 안정적 key, 조건부 PUT, SSE-S3, public ACL 미설정, 412 재사용, 403 실패 확인 |
| S3/DB/Vision 실패·재시도 | 통과 | Vision 실패 시 저장하지 않음, S3 실패 시 Vision checkpoint 유지, DB 실패 시 객체 checkpoint 유지, 재시도 시 Vision/S3 생략, 완료 응답 재사용 |
| 영상 이력 Adapter | 통과 | H2 MySQL mode + 실제 V5 DDL로 중복 claim 거부, 파일 hash 변경 거부, 완료 응답 round-trip, checkpoint 재개 확인 |
| 영상 조회 권한 | 통과 | 사용자 소유 이벤트에만 signer 호출. 타 사용자 조회는 오류, S3 URL은 5분 제한 코드 |
| AUDIO/KEYPAD·Vision 기존 테스트 | 통과 | 기존 트리거/필드/분석 응답·Agent 관련 테스트 포함. 저장 순서 변경에 맞춰 Vision 실패 테스트 기대만 수정 |
| MQTT 실제 로컬 브로커 인증 | 통과 | 제공한 Mosquitto 설정/ACL과 테스트 password 파일 사용. MqttConfig의 인증 옵션으로 연결한 backend → Pi subscriber의 door/sound 전달 확인 |
| MQTT 인증·ACL 거부 | 통과 | 익명 연결 거부, backend door/camera 발행 성공, raspberrypi의 door/sound 발행은 Not authorized로 거부. MQTT v5 publish 거부 시 CLI exit code가 0일 수 있어 응답 메시지로 확인 |
| Mosquitto 헬스체크 | 통과 | 제공한 TCP/파일 확인 명령을 테스트 컨테이너에서 실행 |
| Firebase 파일 설정 | 통과(Mock) | 외부 파일 읽기와 initialize 호출을 Mock 검증. prod 빈 경로/없는 파일의 명확한 초기화 오류 확인. 실제 Firebase 키 내용은 열거나 출력하지 않음 |
| Google TTS 비활성화 | 통과 | prod context가 TTS 주소/디렉터리/Google 인증 없이 기동하고 DisabledTtsAdapter 사용. sound 관련 코드는 변경하지 않음 |
| Docker 이미지 빌드 | 통과 | Java 21 JDK 멀티스테이지 → JRE 이미지, `omniguardy-backend:local-validation` 로컬 태그. push 없음 |
| 이미지 권한·인증파일 제외 | 통과 | UID/GID 10001, /tmp 및 audio 경로 쓰기 가능, Java 21 확인. 최종 이미지에서 꺼낸 JAR entry 검사로 dev/test YAML 및 env/service-account/Google 키 파일 미포함 확인 |
| Docker Compose | 통과 | 합성 테스트 env로 최종 `config --quiet`. 전체 운영 Compose up은 하지 않음 |
| Nginx 구문 | 통과(인증서 제외) | 검증용 복사본에서 ssl listen/certificate 항목을 제거하고 공식 Nginx 이미지로 `nginx -t` 성공. 프록시·limit_conn·업로드/timeout 구문 검사 |
| Git 안전 검사 | 통과 | `git diff --check`, 운영 env/secrets 제외·예제 env 포함 확인. V1~V3와 사운드 코드 변경 없음 |

## 실제 환경에서 아직 확인해야 하는 항목

- EC2의 실제 IAM Role 권한·IMDSv2 접근·S3 업로드/GetObject/Presigned URL 다운로드.
- 실제 Firebase 프로젝트 인증과 Android/iOS 푸시 수신.
- Audio/Vision ngrok 접근성, 실제 Multipart 영상, 처리 시간 및 timeout.
- OpenAI 실제 모델 권한·응답·요금/지연·Agent 실패 후 운영 복구.
- 실제 HTTPS 인증서·갱신·도메인 DNS·보안 그룹·Nginx의 TLS 기동.
- Raspberry Pi SSH 터널/VPN 연결, 카메라/키패드/도어락/경고음/사이렌의 하드웨어 동작.
- React Native의 HTTPS 로그인·Secure Refresh Token 쿠키 저장/갱신 및 영상 URL 재생.
- 1GiB급 호스트의 실제 메모리/swap/디스크/100MiB 업로드 부하. 로컬 테스트는 EC2 성능 검증이 아님.
- 운영 데이터가 있는 기존 DB의 마이그레이션과 백업 복구. 이력이 없는 DB의 baseline은 별도 검토 필요.

일회용 테스트 컨테이너·볼륨·네트워크는 검증 후 정리합니다. 빌드 산출물 및 로컬 검증 이미지는 로컬에 남아 있으며 배포된 서비스는 없습니다.

## 테스트 재실행

일반 Mock/단위 테스트는 `./gradlew build`로 실행합니다. 선택 통합 테스트는 **새로 생성한 일회용 DB/브로커에만** 다음 환경변수를 지정해 실행합니다. 실제 운영 주소를 넣지 마세요.

```text
OMNIGUARDY_TEST_MYSQL_URL=jdbc:mysql://127.0.0.1:<일회용 MySQL 포트>/validation?serverTimezone=UTC
OMNIGUARDY_TEST_MQTT_URL=tcp://127.0.0.1:<일회용 MQTT 포트>
```

테스트 계정/브로커 설정은 `ProdDatabaseIntegrationTest`, `MqttAuthenticationIntegrationTest`의 합성 fixture에 맞춥니다. 환경변수가 없으면 두 통합 테스트 클래스는 건너뜁니다. 테스트 소스의 비밀번호는 로컬 fixture 전용이며 운영에서 사용하지 않습니다.
