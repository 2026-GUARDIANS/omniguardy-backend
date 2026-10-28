# Changed files

The Korean deployment guide and behavior notes are in [README.md](README.md). Test evidence is in [VALIDATION.md](VALIDATION.md).

## Main changes

- Reuse MediaStoragePort: S3 video storage in prod, existing local audio storage, disk-backed multipart video streaming.
- Persist video checkpoints and metadata, including normal KEYPAD videos without SecurityEvent. Reuse existing video_path for object keys.
- Add V4/V5 without changing V1-V3. Use the Boot 4 Flyway starter. Preserve dev/test Hibernate schema management.
- Disable only Google TTS and its file handler in prod. Preserve sound command code and MQTT payloads.
- Load Firebase credentials from an external file in prod, retaining the dev classpath fallback.
- Preserve JWT generation/verification, adding prod Secure cookies, configurable CORS, and proxy headers.
- Add an authenticated owner-checked, five-minute presigned video URL endpoint.
- Preserve the user's pre-existing dev MQTT address edit and development .env. Only move .env import and disable Flyway in dev.
- Exclude dev/test YAML, env files, and credential JSON from the production image.
- Preserve existing domain models that double as JPA entities; isolate new journal persistence in a JDBC adapter and S3 behind application ports.

## Created files

- [.dockerignore](../.dockerignore)
- [Dockerfile](../Dockerfile)
- [deploy/.env.prod.example](.env.prod.example)
- [deploy/CHANGES.md](CHANGES.md)
- [deploy/README.md](README.md)
- [deploy/VALIDATION.md](VALIDATION.md)
- [deploy/aws/ec2-s3-policy.example.json](aws/ec2-s3-policy.example.json)
- [deploy/aws/s3-lifecycle.example.json](aws/s3-lifecycle.example.json)
- [deploy/docker-compose.prod.yml](docker-compose.prod.yml)
- [deploy/mosquitto/acl](mosquitto/acl)
- [deploy/mosquitto/mosquitto.conf](mosquitto/mosquitto.conf)
- [deploy/nginx/omniguardy-proxy.conf](nginx/omniguardy-proxy.conf)
- [deploy/nginx/omniguardy.conf.example](nginx/omniguardy.conf.example)
- [src/main/java/com/omniguardy/backend/domain/detection/application/port/out/VideoUploadJournal.java](../src/main/java/com/omniguardy/backend/domain/detection/application/port/out/VideoUploadJournal.java)
- [src/main/java/com/omniguardy/backend/domain/detection/infrastructure/fastapi/MediaResource.java](../src/main/java/com/omniguardy/backend/domain/detection/infrastructure/fastapi/MediaResource.java)
- [src/main/java/com/omniguardy/backend/domain/detection/infrastructure/storage/JdbcVideoUploadJournal.java](../src/main/java/com/omniguardy/backend/domain/detection/infrastructure/storage/JdbcVideoUploadJournal.java)
- [src/main/java/com/omniguardy/backend/domain/detection/infrastructure/storage/S3MediaStorageAdapter.java](../src/main/java/com/omniguardy/backend/domain/detection/infrastructure/storage/S3MediaStorageAdapter.java)
- [src/main/java/com/omniguardy/backend/domain/detection/infrastructure/storage/S3StorageConfig.java](../src/main/java/com/omniguardy/backend/domain/detection/infrastructure/storage/S3StorageConfig.java)
- [src/main/java/com/omniguardy/backend/domain/detection/infrastructure/storage/S3VideoUrlAdapter.java](../src/main/java/com/omniguardy/backend/domain/detection/infrastructure/storage/S3VideoUrlAdapter.java)
- [src/main/java/com/omniguardy/backend/domain/securityevent/application/port/in/QueryVideoUrlInputPort.java](../src/main/java/com/omniguardy/backend/domain/securityevent/application/port/in/QueryVideoUrlInputPort.java)
- [src/main/java/com/omniguardy/backend/domain/securityevent/application/port/out/VideoUrlPort.java](../src/main/java/com/omniguardy/backend/domain/securityevent/application/port/out/VideoUrlPort.java)
- [src/main/java/com/omniguardy/backend/domain/securityevent/application/usecase/QueryVideoUrlUseCase.java](../src/main/java/com/omniguardy/backend/domain/securityevent/application/usecase/QueryVideoUrlUseCase.java)
- [src/main/java/com/omniguardy/backend/domain/securityevent/presentation/VideoUrlController.java](../src/main/java/com/omniguardy/backend/domain/securityevent/presentation/VideoUrlController.java)
- [src/main/java/com/omniguardy/backend/domain/tts/infrastructure/delivery/DisabledTtsAdapter.java](../src/main/java/com/omniguardy/backend/domain/tts/infrastructure/delivery/DisabledTtsAdapter.java)
- [src/main/java/com/omniguardy/backend/global/config/ProdConfigurationValidator.java](../src/main/java/com/omniguardy/backend/global/config/ProdConfigurationValidator.java)
- [src/main/resources/db/migration/V4__create_missing_base_tables.sql](../src/main/resources/db/migration/V4__create_missing_base_tables.sql)
- [src/main/resources/db/migration/V5__create_video_upload_journal.sql](../src/main/resources/db/migration/V5__create_video_upload_journal.sql)
- [src/test/java/com/omniguardy/backend/domain/detection/infrastructure/storage/JdbcVideoUploadJournalTest.java](../src/test/java/com/omniguardy/backend/domain/detection/infrastructure/storage/JdbcVideoUploadJournalTest.java)
- [src/test/java/com/omniguardy/backend/domain/detection/infrastructure/storage/S3MediaStorageAdapterTest.java](../src/test/java/com/omniguardy/backend/domain/detection/infrastructure/storage/S3MediaStorageAdapterTest.java)
- [src/test/java/com/omniguardy/backend/domain/securityevent/application/usecase/QueryVideoUrlUseCaseTest.java](../src/test/java/com/omniguardy/backend/domain/securityevent/application/usecase/QueryVideoUrlUseCaseTest.java)
- [src/test/java/com/omniguardy/backend/global/config/FirebaseConfigTest.java](../src/test/java/com/omniguardy/backend/global/config/FirebaseConfigTest.java)
- [src/test/java/com/omniguardy/backend/global/config/MqttAuthenticationIntegrationTest.java](../src/test/java/com/omniguardy/backend/global/config/MqttAuthenticationIntegrationTest.java)
- [src/test/java/com/omniguardy/backend/global/config/ProdDatabaseIntegrationTest.java](../src/test/java/com/omniguardy/backend/global/config/ProdDatabaseIntegrationTest.java)

## Modified files

- [.gitignore](../.gitignore)
- [build.gradle](../build.gradle)
- [src/main/java/com/omniguardy/backend/domain/ai/infrastructure/fastapi/FastApiMediaAnalysisAdapter.java](../src/main/java/com/omniguardy/backend/domain/ai/infrastructure/fastapi/FastApiMediaAnalysisAdapter.java)
- [src/main/java/com/omniguardy/backend/domain/auth/presentation/AuthController.java](../src/main/java/com/omniguardy/backend/domain/auth/presentation/AuthController.java)
- [src/main/java/com/omniguardy/backend/domain/detection/application/model/MediaFile.java](../src/main/java/com/omniguardy/backend/domain/detection/application/model/MediaFile.java)
- [src/main/java/com/omniguardy/backend/domain/detection/application/usecase/ReceiveVideoUseCase.java](../src/main/java/com/omniguardy/backend/domain/detection/application/usecase/ReceiveVideoUseCase.java)
- [src/main/java/com/omniguardy/backend/domain/detection/domain/error/DetectionErrorCode.java](../src/main/java/com/omniguardy/backend/domain/detection/domain/error/DetectionErrorCode.java)
- [src/main/java/com/omniguardy/backend/domain/detection/infrastructure/fastapi/AudioFastApiAdapter.java](../src/main/java/com/omniguardy/backend/domain/detection/infrastructure/fastapi/AudioFastApiAdapter.java)
- [src/main/java/com/omniguardy/backend/domain/detection/infrastructure/fastapi/VisionFastApiAdapter.java](../src/main/java/com/omniguardy/backend/domain/detection/infrastructure/fastapi/VisionFastApiAdapter.java)
- [src/main/java/com/omniguardy/backend/domain/detection/presentation/EdgeVideoController.java](../src/main/java/com/omniguardy/backend/domain/detection/presentation/EdgeVideoController.java)
- [src/main/java/com/omniguardy/backend/domain/securityevent/application/usecase/RecordKeypadVisionUseCase.java](../src/main/java/com/omniguardy/backend/domain/securityevent/application/usecase/RecordKeypadVisionUseCase.java)
- [src/main/java/com/omniguardy/backend/domain/tts/domain/error/TtsErrorCode.java](../src/main/java/com/omniguardy/backend/domain/tts/domain/error/TtsErrorCode.java)
- [src/main/java/com/omniguardy/backend/domain/tts/infrastructure/delivery/TtsService.java](../src/main/java/com/omniguardy/backend/domain/tts/infrastructure/delivery/TtsService.java)
- [src/main/java/com/omniguardy/backend/global/config/FirebaseConfig.java](../src/main/java/com/omniguardy/backend/global/config/FirebaseConfig.java)
- [src/main/java/com/omniguardy/backend/global/config/SecurityConfig.java](../src/main/java/com/omniguardy/backend/global/config/SecurityConfig.java)
- [src/main/java/com/omniguardy/backend/global/config/WebConfig.java](../src/main/java/com/omniguardy/backend/global/config/WebConfig.java)
- [src/main/java/com/omniguardy/backend/global/mqtt/MqttConfig.java](../src/main/java/com/omniguardy/backend/global/mqtt/MqttConfig.java)
- [src/main/java/com/omniguardy/backend/global/util/CookieUtil.java](../src/main/java/com/omniguardy/backend/global/util/CookieUtil.java)
- [src/main/resources/application-dev.yml](../src/main/resources/application-dev.yml)
- [src/main/resources/application-prod.yml](../src/main/resources/application-prod.yml)
- [src/main/resources/application-test.yml](../src/main/resources/application-test.yml)
- [src/main/resources/application.yml](../src/main/resources/application.yml)
- [src/test/java/com/omniguardy/backend/domain/detection/application/usecase/ReceiveVideoUseCaseTest.java](../src/test/java/com/omniguardy/backend/domain/detection/application/usecase/ReceiveVideoUseCaseTest.java)
- [src/test/java/com/omniguardy/backend/domain/user/application/usecase/DemoUserResolverTest.java](../src/test/java/com/omniguardy/backend/domain/user/application/usecase/DemoUserResolverTest.java)

## Preserved

Development docker-compose.dev.yml, V1/V2/V3 migration contents, sound/siren implementation, OpenAI Agent business rules, local audio paths, actual secrets, and existing user edits were preserved. No AWS resources or production data were changed.
