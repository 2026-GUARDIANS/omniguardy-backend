# Silent Signal EARLY_EVENT 배포 전 검토 (2026-11-04)

이번 검토는 소스, MySQL 공식 문서, 로컬 H2/Mock만 사용했다. 운영 EC2, 실제 MySQL, Firebase, MQTT 장치에는 연결하지 않았다. 운영 배포 및 Git commit/push도 하지 않았다. 실제 MySQL 8에서의 성공을 확인한 결과는 아니며, 아래 격리 환경 통합 검증을 배포 조건으로 남긴다.

## 1. 실제 버그와 최소 수정

| 구분 | 문제 / 영향 | 적용한 변경 |
|---|---|---|
| 실제 버그 | KEYPAD의 `DemoUserResolver.resolve()`는 미설정 사용자/삭제된 사용자에 대해 빈 Optional 대신 BusinessException을 던진다. 외부 호출 전인데도 FCM이 UNCERTAIN이 되어 최종 HIGH/CRITICAL 알림까지 억제될 수 있었다. | 조기 발송 Adapter에서 `DEMO_USER_ID_NOT_CONFIGURED`, `USER_NOT_FOUND`만 NO_RECIPIENT으로 처리한다. FCM은 SKIPPED, SOUND는 독립 실행하며, 나중에 소유자가 있는 최종 이벤트는 알림을 보낼 수 있다. DB 장애 등 다른 예외는 계속 실패로 처리한다. |
| 실제 버그 | KEYPAD claim의 이벤트 조회는 기존 `security_events.trigger_id`가 대소문자를 구분하지 않는 collation이면 다른 trigger를 후보로 반환할 수 있었다. 잘못 연결하면 Adapter에서 binding 오류가 나거나 이후 정상 최종 이벤트가 binding mismatch로 거부될 수 있다. | 후보 조회 후 Java의 정확한 문자열 비교로 trigger가 일치하는 이벤트만 연결한다. 일치하는 이벤트가 여러 개면 임의로 선택하지 않는다. |
| 배포 방어 보강 | ledger의 행 잠금/롤백 보장이 서버 기본 엔진에 의존했다. | 신규 V12 테이블에 `ENGINE=InnoDB`를 명시한다. 이미 기본 엔진이 InnoDB라면 동작은 같다. |

V12는 아직 배포하지 않은 마이그레이션이라는 전제로 수정했다. 어느 환경이든 V12를 이미 적용했다면 그 환경의 Flyway checksum을 무시하거나 임의 repair하지 말고, 적용 이력에 맞는 별도 후속 마이그레이션을 준비해야 한다.

## 2. MySQL 8 및 기존 데이터 검토

- V12의 BOOLEAN, DATETIME(6), UTC_TIMESTAMP(6), utf8mb4_bin, 집계 INSERT SELECT, 인덱스는 MySQL 8 문법이다. SELECT의 비집계 trigger_type/trigger_id는 모두 GROUP BY에 있으므로 ONLY_FULL_GROUP_BY를 끌 필요가 없다. response_key 최대 길이는 `KEYPAD:` 7자 + trigger 64자 = 71자로 VARCHAR(80)에 들어간다.
- CHECK 제약이 실제로 강제되는 MySQL **8.0.16 이상**을 전제로 한다. InnoDB, strict SQL mode, 실제 서버/DB/컬럼 문자셋 및 collation을 격리 복제 환경에서 확인한다.
- 기존 테이블 ALTER/UPDATE/DELETE와 신규 FK가 없다. 기존 Vision/Agent/MP4/Fallback 및 사용자 데이터 구조를 바꾸지 않는다.
- 기존 `vision_early_events`는 trigger별 한 행으로 집계되어 `early_received=true`, 두 채널 level=3 / SKIPPED로 등록된다. 동일 trigger의 재전송이나 새 instance도 과거 사이렌을 재가동하지 않는다. SKIPPED이므로 일반 최종 Agent 대응은 허용된다. 신규 trigger에는 정상 조기 발송이 예약된다.
- MIN(session_id)/MIN(received_at)/MAX(security_event_id)는 독립 집계다. 정상적으로 trigger당 한 binding이라는 전제가 깨진 데이터에서는 임의 조합이 될 수 있으므로 아래 사전 데이터 감사를 수행한다. 충돌 데이터를 자동 보정하지 않는다.
- 신규 테이블 생성과 backfill은 기존 관측 테이블을 읽고 그룹화하므로 데이터가 많으면 시작 시간/잠금 대기가 늘 수 있다. 운영 규모의 비식별 복제본으로 실행 시간과 여유 공간을 측정한다.
- MySQL의 atomic DDL은 **문장 단위**다. CREATE TABLE, CREATE INDEX, INSERT 전체가 하나로 롤백되지는 않는다. 중간 실패 후 테이블/인덱스가 남을 수 있으므로 재시작 반복이나 무조건적인 Flyway repair로 해결하지 않는다. 실패 단계, Flyway 이력, 잔여 구조/데이터를 먼저 대조한다. 무발송 상태의 격리 복제본에서 복구 절차를 검증한다.
- 구버전 인스턴스가 migration 이후 계속 EARLY_EVENT/최종 대응을 처리하면 신규 ledger와 구버전 발송 정책이 혼재한다. 전환 중 callback 유입과 구버전 작업 처리를 통제하고, 구버전 인스턴스와 신버전 Worker가 겹쳐 대응하지 않도록 배포 순서를 정한다.

근거: [MySQL CHECK 제약과 8.0.16](https://dev.mysql.com/doc/refman/8.0/en/alter-table.html), [GROUP BY](https://dev.mysql.com/doc/refman/8.0/en/group-by-handling.html), [InnoDB locking reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html), [atomic DDL과 implicit commit](https://dev.mysql.com/doc/refman/8.0/en/atomic-ddl.html).

## 3. Spring 시작 및 트랜잭션/복구 경계

1. callback은 session/trigger/event를 검증하고, `JdbcVisionEarlyEventRepository.insert()`의 REQUIRES_NEW 트랜잭션으로 관측값을 먼저 커밋한다. 중복 insert는 별도 롤백 후 기존 payload와 비교한다.
2. `enqueueEarly()`는 별도의 REQUIRES_NEW 트랜잭션에서 ledger를 생성/잠그고 FCM과 SOUND PENDING을 **함께** 커밋한다. SOUND 예약 실패 시 FCM 예약과 신규 ledger도 롤백된다. 관측값은 남으므로 다음 복구 스케줄이 다시 예약한다. 저장 후 예약이 실패하면 HTTP 오류가 날 수 있지만, 동일 callback 재전송도 복구 경로다.
3. 커밋 후 `EarlyWarningKick.request()`가 전용 FCM/SOUND executor에 각각 제출한다. 순차적인 FCM 완료 → SOUND 호출 구조가 아니다. FCM 장애/지연이 SOUND worker를 점유하지 않는다.
4. `claimEarly()`는 REQUIRES_NEW에서 행 잠금과 채널별 token으로 PENDING → CALLING을 커밋하고 반환한다. 그 후 외부 Adapter를 호출한다. 완료 UPDATE도 REQUIRES_NEW이며, 현재 CALLING token과 일치할 때만 상태를 변경한다. 옛 level=2 호출의 늦은 완료가 새 level=3 예약을 덮어쓰지 않는다.
5. 발송 전체가 DB 트랜잭션 밖이라는 뜻은 아니다. FCM Adapter/NotificationService는 사용자/토큰 조회와 notification 저장 및 provider 호출을 트랜잭션으로 감싼다. ledger 행 잠금은 이 호출 동안 유지하지 않는다. provider 성공 후 notification DB commit 실패는 외부 발송을 취소할 수 없어 UNCERTAIN으로 남을 수 있다. 여러 토큰 중 일부만 발송된 경우도 전체 재전송하지 않는다.
6. 프로세스 종료가 관측 저장 뒤에 발생하면 `undispatchedSessions()`가 재예약한다. PENDING 상태 종료는 다음 시작/스케줄에서 재처리한다. CALLING 직후 종료 또는 외부 응답 유실은 중복 가능성을 구분할 수 없으므로 자동 재발송하지 않는다.

Spring component scan으로 두 executor, `earlyWarningTaskScheduler`, `EarlyWarningKick`이 등록된다. 기존 `StreamingVisionConfiguration`의 @EnableScheduling이 스케줄 처리를 활성화하고, recover는 명시적인 earlyWarningTaskScheduler를 사용한다. 기존 기본 Vision scheduler / Agent / MP4 link / Fallback scheduler와 별도 인스턴스다. 기본 복구 간격은 1000ms이며 `omniguardy.early-warning.recovery-delay-ms`로 조정한다.

각 executor는 실행 1개 + 대기열 1개로 제한된다. 초과 제출은 거부되지만 업무 데이터는 DB에 남고, 독립 scheduler가 주기적으로 새로운 dispatch를 제출한다. batch당 채널별 최대 20건을 처리하므로 backlog가 크면 여러 tick이 필요하다. **worker가 다시 실행 여력을 얻는다는 조건에서** 포화 후 복구된다. provider가 영구 정지하면 해당 채널의 단일 worker도 정지하므로 scheduler만으로 강제 해제할 수 없다. 다른 채널은 별도 worker지만 DB pool과 DB 가용성은 공유한다.

## 4. 유지한 정책 선택과 사용자 영향

| 조기 채널 상태 | 최종 MEDIUM / HIGH / CRITICAL의 같은 채널 |
|---|---|
| PENDING, CALLING, SUCCEEDED, UNCERTAIN (level 3) | 추가 알림/재생 생략 |
| SKIPPED (소유자/활성 토큰 없음 또는 과거 데이터 backfill) | 일반 최종 대응 허용 |
| 조기 대응 없음 | 기존 최종 대응. MEDIUM level 2, HIGH/CRITICAL level 3 |

- 최종 Agent 판단, reason/risk 저장, 이벤트 완료, Vision 결과, 영상 저장/연결은 계속 진행한다. 생략되는 것은 외부 대응과 해당 최종 notification 생성이다.
- HIGH와 CRITICAL은 모두 level=3이므로 CRITICAL도 별도 확정 알림을 만들지 않는다. 사용자는 “최종 분석은 진행 중” 조기 메시지만 보고 최종 결과는 조회해야 할 수 있다. KEYPAD 조기 메시지는 eventId가 없을 수 있어 최종 이벤트 상세로 바로 이동하지 못할 수도 있다. 나중에 ledger에 eventId를 연결해도 이미 발송된 FCM payload는 바뀌지 않는다.
- PENDING/UNCERTAIN도 최종 알림을 억제한다. 중복을 줄이는 대신 실제 미발송/불확실 상태에서 최종 대응까지 생략될 수 있다. CALLING 예약 직후 종료되어 아직 provider를 호출하지 않았더라도 자동 재시도하지 않는다. 이는 현재 정책이며 exactly-once 전달 보장은 아니다.
- **KEYPAD 연결 시점 주의:** 최종 판단이 PENDING/CALLING 중에 이미 억제되고, 이후 조기 FCM이 수신자 없음으로 SKIPPED가 되면 그 최종 알림은 소급 실행되지 않는다. “SKIPPED는 최종 대응 허용”은 최종 대응이 SKIPPED 이후 실행되는 경우의 규칙이다. eventId 연결/소유자 복구만으로 끝난 Agent를 재실행하지 않는다. 이 경합도 테스트로 고정했다. 이 누락 가능성을 허용할 수 없다면 조기 SKIPPED 뒤의 최종 알림 보완을 별도 durable 작업으로 설계해야 한다.
- LOW 최종 판단은 이미 예약한 조기 사이렌을 취소하지 않는다. 반대로 먼저 완료된 MEDIUM 후 조기 level=3 대응은 한 번 상향될 수 있다. 이미 시작된 외부 level=2 호출을 취소할 수는 없다.
- “최종 확정 결과는 별도 알림”, “UNCERTAIN에서 시간 경과 후 재발송”, “KEYPAD eventId 생성/소유자·토큰 복구만으로 SKIPPED 재가동”은 제품 정책 변경이다. 이번 수정에서는 추가하지 않았다.
- SUCCEEDED는 FCM provider API 반환 / MQTT client handoff 성공이다. 앱 수신·읽음, broker delivery, Pi 수신·사이렌 재생을 증명하지 않는다. MQTT QoS 1이나 provider 내부 재시도까지 포함한 외부 중복 방지는 ledger만으로 보장되지 않는다.

## 5. KEYPAD 연결 예외

- 최종 이벤트 이전: 검증된 keypad trigger와 기존 demo 사용자 규칙으로 대응한다. 가짜 SecurityEvent나 eventId를 만들지 않는다. 소유자 없음/활성 토큰 없음은 FCM SKIPPED, SOUND는 실행한다.
- 최종 이벤트가 claim 전에 생성되면 정확히 같은 trigger의 유일한 이벤트를 연결하여 실제 이벤트 소유자로 보낸다.
- claim 이후 이벤트 생성은 다음 `linkCreatedEvents()` 또는 `claimFinal()`에서 연결한다. 이미 실행한 FCM을 다시 보내지는 않는다.
- 정확히 일치하는 이벤트가 두 개 이상이면 복구/claim이 임의 eventId를 선택하지 않는다. eventId 없는 조기 경고는 기존 소유자 규칙을 사용할 수 있으므로, 중복 이벤트는 운영자가 별도로 감사해야 한다.
- ledger가 이미 다른 eventId에 연결되어 있으면 최종 claim이 binding mismatch로 발송을 거부한다. 식별자를 수동으로 갈아 끼우거나 상태만 초기화하지 않는다. callback의 securityEventId=NULL 계약과 MP4의 별도 연결 계약은 유지한다.
- 수정 후에도 **최종 이벤트 자체에 user가 없으면** 최종 FCM은 기존 동작대로 생략된다. SKIPPED가 소유자/토큰 등록 즉시 자동 재발송된다는 의미는 아니다.

## 6. 운영 관찰 방법 (조회 제안, 이번 작업에서 실행하지 않음)

현재 ledger를 조회하는 별도 운영 API는 없다. 기존 읽기 전용 DB 접근과 로그 수집에서 아래를 확인하는 방법을 권장한다. `updated_at`은 두 채널이 공유하므로 채널별 호출 시작 시각과 정확히 같지는 않다.

```sql
-- 불확실 발송/누적 작업 목록. token/사용자 정보는 출력하지 않는다.
SELECT response_key, trigger_type, trigger_id, event_id, session_id,
       fcm_level, fcm_state, sound_level, sound_state, received_at, updated_at
FROM security_response_ledger
WHERE fcm_state IN ('PENDING','CALLING','UNCERTAIN')
   OR sound_state IN ('PENDING','CALLING','UNCERTAIN')
ORDER BY updated_at;

-- 오래 정체된 작업 후보. 5분은 시작 기준이며 실제 provider timeout에 맞춰 조정한다.
SELECT response_key, fcm_state, sound_state, updated_at
FROM security_response_ledger
WHERE updated_at < UTC_TIMESTAMP(6) - INTERVAL 5 MINUTE
  AND (fcm_state IN ('PENDING','CALLING') OR sound_state IN ('PENDING','CALLING'));

-- 연결이 아직 없거나 모호한 KEYPAD ledger
SELECT response_key, trigger_id, fcm_state, sound_state, updated_at
FROM security_response_ledger WHERE trigger_type='KEYPAD' AND event_id IS NULL;

-- 배포 전: 과거 관측의 trigger별 binding 불일치 후보
SELECT trigger_type, trigger_id, COUNT(DISTINCT session_id) AS sessions,
       COUNT(DISTINCT security_event_id) AS events
FROM vision_early_events GROUP BY trigger_type, trigger_id
HAVING COUNT(DISTINCT session_id)>1 OR COUNT(DISTINCT security_event_id)>1;

-- 기존 이벤트의 정확한 trigger 중복 후보 (레거시 collation의 영향 제외)
SELECT trigger_type, CAST(trigger_id AS BINARY) AS exact_trigger, COUNT(*) AS events
FROM security_events WHERE trigger_id IS NOT NULL
GROUP BY trigger_type, CAST(trigger_id AS BINARY) HAVING COUNT(*)>1;
```

- UNCERTAIN 새 발생은 즉시 확인 대상으로, CALLING 장기 정체와 PENDING 최고 대기 시간/건수는 경보 대상으로 삼는다. `NO_RECIPIENT`, `NO_ACTIVE_TOKENS`도 누락 원인 지표로 집계한다. 대기열 포화 자체는 유실의 증거가 아니며 DB backlog의 감소 여부가 중요하다.
- `sessionId`/`triggerId`로 `EARLY_EVENT_HTTP`, `EARLY_EVENT_STORE`, `EARLY_EVENT_FCM_REQUEST`, `EARLY_EVENT_MQTT_SIREN`, `EARLY_EVENT_TO_FCM`, `EARLY_EVENT_TO_SOUND`, `FCM_SEND_REQUEST`, `MQTT_SOUND_COMMAND`를 대조한다. KEYPAD는 초기 eventId가 없을 수 있다.
- CALLING/UNCERTAIN은 앱·provider·장치 쪽 확인과 백엔드 로그를 함께 조사한다. 실제 수신 확인 없이 자동 PENDING 초기화하면 중복 알림/사이렌을 만들 수 있다. 재발송은 해당 worker의 실행 종료/재시작 여부 및 실제 전달 여부를 확인하고 별도 운영 판단으로 수행한다. 이번 변경은 자동 상태 초기화나 재발송 명령을 추가하지 않는다.

## 7. 검증 및 회귀 위험

H2의 MySQL 모드에서는 MySQL 전용 collation 구문을 제거하고 UTC_TIMESTAMP를 CURRENT_TIMESTAMP로 치환한다. 따라서 H2 결과가 MySQL DDL/잠금/격리/실행 계획 검증을 대체하지 않는다.

최종 로컬 검증: `./gradlew.bat build --offline` **성공**. JUnit XML 합계 **450개: 성공 439개, 실패/오류 0개, skipped 11개**. 기존 성공 426개에서 테스트 13개를 추가했다. skipped는 기존 MySQL 9개, MQTT 1개, disabled smoke 1개다. `git diff --check`도 통과했다. 외부 통합 테스트가 활성화되지 않도록 실행 프로세스에서 두 환경변수를 제거했다.

```powershell
$env:OMNIGUARDY_TEST_MYSQL_URL=$null
$env:OMNIGUARDY_TEST_MQTT_URL=$null
./gradlew.bat build --offline
```

추가 테스트 범위:

- 실제 소유자 미설정/삭제 예외는 NO_RECIPIENT, DB 오류는 실패로 유지, 연결된 KEYPAD는 실제 이벤트 소유자 사용.
- 대소문자를 구분하지 않는 레거시 이벤트 컬럼에서도 정확한 trigger만 연결.
- 두 bounded queue를 모두 포화시키고 callback 제출을 거부한 뒤, 자동 @Scheduled tick만으로 DB PENDING을 처리.
- callback kick 없이 관측 저장만 남은 경우 자동 복구; 예약 실패 시 두 채널 원자적 롤백 후 재예약.
- 외부 delivery port 호출 시 ledger transaction 종료 및 CALLING 커밋 확인.
- 수신자 없는 KEYPAD가 최종 CRITICAL 이벤트 연결 후 FCM만 재시도하고 사이렌은 반복하지 않음.
- 최종 판단이 CALLING 중 억제된 후 조기 FCM이 SKIPPED가 되는 경합에서는 최종 FCM 자동 재생이 없음(현재 정책의 한계).
- 최종 이벤트 생성 시점, 이벤트 중복, 과거 관측 다중 instance/대소문자/최대 길이 trigger의 backfill 및 기존 데이터 보존.
- 전체 Spring Boot H2 컨텍스트에서 recover의 실제 scheduled task 등록, 기존 네 scheduler와의 분리, queue capacity 확인.

기존 동시 callback/Agent/Worker 경합, 재전송, 옛 token fencing, CALLING 재시작, FCM/SOUND 장애 분리, HTTP 인증/계약, Vision polling, Agent 최종 저장, MP4 저장/링크, Fallback 회귀도 전체 빌드에 포함한다. 신규 ledger는 최종 발송에도 사용되므로 조기 경고가 없는 이벤트도 최종 발송 시 ledger DB 가용성에 의존한다. DB 예약 실패는 외부 호출을 하지 않고 기존 RiskResponseService가 로그를 남긴다. 최종 대응 자체는 PENDING 복구 큐가 아니므로 최종 claim 이전 실패까지 자동 재시도한다고 해석하면 안 된다.

## 8. 배포 전 확인 및 실제 통합 테스트 순서

1. **정책 확인:** 조기 경고 뒤 최종 HIGH/CRITICAL 알림 생략, LOW 후 사이렌 유지, CALLING/UNCERTAIN 자동 미재시도, eventId 없는 KEYPAD 메시지의 앱 동작을 확인한다. demo 소유자 설정/실제 사용자 존재/활성 token을 점검한다.
2. **격리 MySQL 8.0.16+ 준비:** 운영과 같은 minor version, charset/collation, SQL mode, InnoDB 및 시간대 설정을 사용한다. 실제 운영 DB에 테스트를 실행하지 않는다. 외부 발송은 Mock/격리 endpoint로 유지한다.
3. **빈 DB Flyway:** V1~V12 순서 적용, validate 및 재시작을 확인한다. V12 테이블 엔진, CHECK/PK/인덱스, 문자열 대소문자와 최대 길이를 검사한다.
4. **기존 데이터 upgrade:** V11 DB의 비식별 복제본에 과거 AUDIO/KEYPAD 관측, 다중 instance, 완료/미완료 이벤트, 영상/Fallback 작업을 넣고 V12를 적용한다. trigger별 SKIPPED backfill, 기존 행 불변, 과거 재전송 무발송, 신규 trigger 정상 발송을 검증한다. backfill 시간/공간/잠금 대기를 측정한다. migration 실패 단계별 잔여 DDL/Flyway 이력 복구도 격리 환경에서 연습한다.
5. **Spring Boot + 실제 MySQL + Mock 외부 발송:** scheduler/worker bean 시작, 동시 callback/최종 Agent 경쟁, 두 채널 예약 롤백, claim token fencing, case-sensitive trigger 연결을 검사한다. H2와 다른 InnoDB deadlock/lock wait는 재시도와 PENDING 회복까지 확인한다.
6. **장애/포화:** 관측 commit 직후, PENDING commit 직후, CALLING commit 직후, provider 반환 직후 각각 프로세스를 종료/재시작한다. 앞 두 경우 재처리, CALLING 이후는 자동 재발송 없음, FCM 지연 중 SOUND 독립 실행, 큐 포화 해소 후 backlog 감소를 확인한다. DB 장애와 여러 token 일부 성공도 포함한다.
7. **격리 Firebase/MQTT 통합:** 테스트 프로젝트/token과 테스트 broker/안전한 장치를 사용한다. AUDIO/KEYPAD 조기 메시지 문구·payload·앱 라우팅, MQTT topic/payload, 실제 기기 수신/재생을 확인한다. FCM API 반환과 실제 수신, MQTT handoff와 장치 재생을 구분하여 기록한다. 이후 HIGH/CRITICAL 추가 알림 생략 및 수신자/토큰 없는 경우 최종 대응 허용을 확인한다.
8. **최종 회귀와 전환 준비:** 실제 Vision 최종 처리 → Agent 결과 저장 → MP4 저장/조회/KEYPAD 연결 → Fallback을 순서대로 점검한다. Flyway 이력/백업, CALLING/UNCERTAIN 관찰 담당, 구·신버전 중첩 방지, 롤백 시 ledger 보존과 구버전 발송 중복 가능성을 확인한 뒤 배포 절차로 넘긴다.

위 항목은 실행 순서 제안이다. 이번 작업에서는 실제 MySQL/외부 서비스 통합 및 운영 전환을 수행하지 않았다.
