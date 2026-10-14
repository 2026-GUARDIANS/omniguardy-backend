# DDD 패키지 규칙

백엔드는 DDD(Domain-Driven Design)를 기반으로 구성하며,
각 Bounded Context의 책임과 의존성 방향을 명확하게 분리한다.

모든 Bounded Context는 다음 경로 아래에 위치한다.

`com.omniguardy.backend.domain.<context>`

예시:

com.omniguardy.backend.domain
├── audio
├── vision
├── risk
├── agent
└── notification

각 Bounded Context는 필요한 경우 다음 구조를 따른다.

<context>
├── presentation
├── application
│   ├── port
│   │   ├── in
│   │   └── out
│   └── service
├── domain
│   ├── model
│   ├── repository
│   └── policy
└── infrastructure
    ├── persistence
    └── external


## 1. 기본 의존성 규칙

레이어 간 의존성 방향은 다음을 따른다.

presentation
      ↓
application
      ↓
domain

infrastructure
      ↓
application / domain

핵심 원칙:

- `presentation`은 `application`을 통해서만 비즈니스 기능을 호출한다.
- `application`은 Use Case를 조정하고 Domain 로직을 사용한다.
- `domain`은 핵심 비즈니스 규칙만 가진다.
- `infrastructure`는 외부 시스템 및 기술 구현을 담당한다.
- Domain은 Presentation 또는 Infrastructure에 의존해서는 안 된다.
- Application은 구체적인 Infrastructure 구현체에 직접 의존하지 않는다.


## 2. Presentation

`presentation`은 HTTP/API 경계를 담당한다.

주요 책임:

- Controller 구현
- HTTP Request / Response DTO 정의
- 요청 데이터 검증
- HTTP DTO → Application Command/Query 변환
- Application Use Case 호출
- Application/Domain 결과 → Response DTO 변환

금지 사항:

- 비즈니스 로직 작성 금지
- Repository 직접 호출 금지
- Infrastructure Adapter 직접 호출 금지
- JPA Entity 직접 사용 금지
- 외부 AI API 직접 호출 금지

의존성:

presentation → application


## 3. Application

`application`은 시스템의 Use Case를 조정한다.

주요 책임:

- Use Case 정의
- Application Service 구현
- Domain 객체 및 정책 호출
- 여러 Domain 작업의 흐름 조정
- 외부 시스템 호출을 위한 Port 정의
- 트랜잭션 경계 관리

권장 구조:

application
├── port
│   ├── in
│   └── out
└── service

### Inbound Port

시스템이 외부에 제공하는 Use Case를 정의한다.

예:

application.port.in
├── AnalyzeRiskUseCase
├── ProcessAudioEventUseCase
└── ProcessVisionResultUseCase

Controller는 반드시 Inbound Port를 통해 Application 기능을 호출한다.


### Outbound Port

Application이 외부 시스템에 요구하는 기능을 정의한다.

예:

application.port.out
├── AudioAiPort
├── VisionAiPort
├── AgentAiPort
├── NotificationPort
└── VideoStoragePort

Application은 실제 구현체를 알지 못하며 Port Interface에만 의존한다.


## 4. Domain

`domain`은 시스템의 핵심 비즈니스 규칙을 담당한다.

포함 대상:

- Entity
- Aggregate
- Value Object
- Domain Service
- Domain Policy
- Repository Contract
- Domain Exception

예:

domain
├── model
│   ├── RiskEvent
│   ├── RiskLevel
│   └── AnalysisResult
├── repository
│   └── RiskEventRepository
└── policy
    └── RiskDecisionPolicy

Domain은 기술 구현과 독립적이어야 한다.

Domain에서 다음 항목에 의존하는 것을 금지한다.

- Controller
- Presentation DTO
- HTTP Request / Response
- Infrastructure Adapter
- JPA Entity
- FastAPI Client
- OpenAI Client
- LangChain4j 구현체
- MQTT 구현체
- FCM 구현체

가능하면 Domain에는 Spring Framework 의존성도 두지 않는다.


## 5. Infrastructure

`infrastructure`는 외부 시스템과 기술적인 구현을 담당한다.

예:

- JPA
- Database
- FastAPI
- Audio AI
- Vision AI
- OpenAI
- LangChain4j
- MQTT
- FCM
- Local Storage
- Object Storage

구조 예시:

infrastructure
├── persistence
│   ├── RiskEventJpaEntity
│   ├── RiskEventJpaRepository
│   └── RiskEventRepositoryAdapter
└── external
    ├── audio
    ├── vision
    ├── agent
    ├── mqtt
    └── notification

Infrastructure는 Application의 Outbound Port 또는
Domain의 Repository Contract를 구현한다.


## 6. Port / Adapter 규칙

외부 시스템과의 연결은 반드시 Port / Adapter 구조를 사용한다.

예:

AgentAiPort
     ↑
LangChainAgentAdapter

VisionAiPort
     ↑
VisionFastApiAdapter

AudioAiPort
     ↑
AudioFastApiAdapter

NotificationPort
     ↑
FcmNotificationAdapter

VideoStoragePort
     ↑
LocalVideoStorageAdapter

Application Service는 Adapter 구현체를 직접 호출하지 않는다.

잘못된 예:

RiskService
    ↓
LangChainAgentAdapter

올바른 예:

RiskService
    ↓
AgentAiPort
    ↑
LangChainAgentAdapter


## 7. Repository 규칙

Domain Aggregate 저장/조회에 필요한 Repository Contract는
Domain에 위치시킨다.

예:

domain.repository
└── RiskEventRepository

실제 DB 구현은 Infrastructure에 위치한다.

예:

infrastructure.persistence
├── RiskEventJpaEntity
├── RiskEventJpaRepository
└── RiskEventRepositoryAdapter

변환 구조:

JPA Entity ↔ Domain Entity

JPA Entity를 Application이나 Domain으로 직접 반환하지 않는다.


## 8. DTO 규칙

DTO는 시스템의 Boundary에서만 사용한다.

HTTP 요청/응답 DTO:

presentation.dto

외부 API DTO:

infrastructure.external.<system>.dto

예:

infrastructure.external.audio.dto
├── AudioAnalysisRequest
└── AudioAnalysisResponse

infrastructure.external.vision.dto
├── VisionAnalysisRequest
└── VisionAnalysisResponse

infrastructure.external.agent.dto
├── AgentAnalysisRequest
└── AgentAnalysisResponse

외부 DTO를 Domain Entity로 사용하지 않는다.

변환은 각 Boundary에서 수행한다.

HTTP DTO
    ↓
Application Command
    ↓
Domain

External API DTO
    ↓
Adapter
    ↓
Application / Domain Model


## 9. Bounded Context 간 통신 규칙

Bounded Context끼리 다른 Context의 내부 구현에 직접 의존하지 않는다.

잘못된 예:

audio.infrastructure
        ↓
vision.infrastructure

또는:

audio.domain
        ↓
vision.infrastructure

Context 간 협력이 필요한 경우 다음 중 하나를 사용한다.

1. Application Use Case
2. 명시적인 Port
3. Domain/Application Event

예:

Audio 위험 감지
    ↓
Audio Application
    ↓
CameraStartPort
    ↓
MQTT Adapter

또는:

Vision 분석 완료
    ↓
Risk Application Use Case
    ↓
AgentAiPort
    ↓
Agent Infrastructure Adapter


## 10. Agent AI 구현 규칙

Agent AI는 외부 AI 시스템으로 취급한다.

따라서 LangChain4j 또는 OpenAI 구현이
Domain/Application 로직에 직접 노출되어서는 안 된다.

Application:

application.port.out
└── AgentAiPort

Infrastructure:

infrastructure.external.agent
└── LangChainAgentAdapter

구조:

Risk Application Service
        ↓
AgentAiPort
        ↑
LangChainAgentAdapter
        ↓
LangChain4j / OpenAI

Agent 요청에는 필요한 Audio/Vision 분석 결과를 전달하고,
Agent 응답은 외부 DTO 그대로 사용하지 않고
Application 또는 Domain에서 사용할 수 있는 결과 모델로 변환한다.

예:

Agent 결과
- riskLevel
- reason
- action


## 11. Global 패키지 규칙

여러 Bounded Context에서 공통으로 사용하는 기술 설정은
`global`에 위치한다.

예:

global
├── config
├── exception
├── response
└── security

포함 대상:

- Spring 공통 Configuration
- 공통 API Response Envelope
- Global Exception Handler
- Security 설정
- 공통 기술 설정

주의:

`global`을 공용 비즈니스 로직 저장소처럼 사용하지 않는다.

특정 Domain에 속하는 비즈니스 규칙은 반드시
해당 Bounded Context 내부에 위치시킨다.


## 12. 구현 및 리팩토링 시 필수 규칙

Agent/Codex가 코드를 생성하거나 기존 구조를 리팩토링할 때
다음 규칙을 반드시 준수한다.

1. 기존 비즈니스 동작을 임의로 변경하지 않는다.
2. DDD 구조 변경을 이유로 API Contract를 임의로 변경하지 않는다.
3. Domain에 Infrastructure 의존성을 추가하지 않는다.
4. Controller에서 Repository를 직접 호출하지 않는다.
5. Application에서 Adapter 구현체를 직접 생성하거나 참조하지 않는다.
6. 외부 AI API 호출은 반드시 Outbound Port를 통해 수행한다.
7. JPA Entity와 Domain Entity를 분리한다.
8. 외부 API DTO와 Domain Model을 분리한다.
9. Bounded Context 내부 구현을 다른 Context에서 직접 참조하지 않는다.
10. 공통 기술 설정만 `global`에 위치시킨다.
11. 새로운 클래스 추가 전 기존 클래스와 책임이 중복되지 않는지 확인한다.
12. 기존 기능을 삭제하거나 대규모 변경해야 하는 경우 임의로 진행하지 않는다.
13. 요구사항 범위를 벗어난 코드 수정 및 리팩토링을 하지 않는다.
14. 테스트가 존재하는 경우 기존 테스트가 깨지지 않도록 유지한다.
15. 새로운 Use Case를 구현할 때 의존성 방향이 DDD 규칙을 위반하지 않는지 확인한다.


## 13. 최종 의존성 원칙

전체적으로 다음 원칙을 유지한다.

Presentation → Application → Domain

Infrastructure → Application / Domain

Domain → 외부 레이어 의존 금지

그리고 모든 외부 시스템 연동은 다음 구조를 우선한다.

Application
    ↓
Outbound Port
    ↑
Infrastructure Adapter
    ↓
External System

DDD 리팩토링의 목적은 단순히 패키지를 이동하는 것이 아니라,
비즈니스 규칙과 외부 기술 의존성을 분리하는 것이다.

패키지 이동만 수행하고 기존 의존 관계를 그대로 유지하는 방식은
DDD 구조 변경으로 간주하지 않는다.
