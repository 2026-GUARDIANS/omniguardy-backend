# omniguardy-backend

## OpenAI Agent 환경변수

프로젝트 루트에서 `.env.example`을 복사해 `.env` 파일을 만든 뒤 실제 값을 입력합니다.
`.env`는 Git에서 제외되며 실제 API 키를 `.env.example`, YAML, 소스 코드 또는 커밋에 넣으면 안 됩니다.

```powershell
Copy-Item .env.example .env
```

```dotenv
AGENT_AI_ENABLED=true
OPENAI_API_KEY=실제_API_키
OPENAI_MODEL_NAME=gpt-5-mini
OPENAI_TIMEOUT_SECONDS=60
```

애플리케이션을 프로젝트 루트에서 실행하면 Spring Boot가 `.env`를 선택적으로 읽습니다.
`.env`를 사용하지 않는 배포 환경에서는 같은 이름의 OS 환경변수나 IDE Run Configuration의
Environment variables에 값을 설정할 수 있으며, OS 환경변수가 `.env`보다 우선합니다.

`AGENT_AI_ENABLED=false`이면 API 키 없이 실행할 수 있습니다. `true`인데
`OPENAI_API_KEY`가 비어 있으면 애플리케이션 시작 시 안전하게 실패합니다.
