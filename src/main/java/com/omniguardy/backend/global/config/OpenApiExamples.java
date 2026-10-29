package com.omniguardy.backend.global.config;

public final class OpenApiExamples {

    private OpenApiExamples() {
    }

    public static final String SIGNUP_REQUEST = """
            {"email":"guard@example.com","password":"Guard!2345","name":"김가디","phoneNumber":"010-1234-5678"}
            """;
    public static final String LOGIN_REQUEST = """
            {"email":"guard@example.com","password":"Guard!2345"}
            """;
    public static final String AUTH_SESSION_SUCCESS = """
            {"success":true,"code":"LOGIN_SUCCESS","message":"로그인에 성공했습니다.","data":{"accessToken":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJndWFyZEBleGFtcGxlLmNvbSJ9.signature","tokenType":"Bearer","userId":17,"email":"guard@example.com","name":"김가디"}}
            """;
    public static final String SIGNUP_SUCCESS = """
            {"success":true,"code":"SIGNUP_SUCCESS","message":"회원가입이 완료되었습니다.","data":{"accessToken":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJndWFyZEBleGFtcGxlLmNvbSJ9.signature","tokenType":"Bearer","userId":17,"email":"guard@example.com","name":"김가디"}}
            """;
    public static final String REISSUE_SUCCESS = """
            {"success":true,"code":"TOKEN_REISSUE_SUCCESS","message":"토큰이 재발급되었습니다.","data":{"accessToken":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJndWFyZEBleGFtcGxlLmNvbSJ9.signature","tokenType":"Bearer"}}
            """;
    public static final String LOGOUT_SUCCESS = """
            {"success":true,"code":"LOGOUT_SUCCESS","message":"로그아웃이 완료되었습니다.","data":null}
            """;
    public static final String AI_ANALYZE_SUCCESS = """
            {"success":true,"code":"AI_ANALYSIS_COMPLETED","message":"AI 분석이 완료되었습니다.","data":{"vision":{"module":"vision-analyzer","video":"entrance-20261029.mp4","events":"PERSON_DETECTED","riskScore":82,"riskLevel":"HIGH","annotatedVideo":"https://cdn.omniguardy.cloud/annotated/entrance-20261029.mp4"},"audio":{"eventType":"KNOCK","probability":0.94}}}
            """;
    public static final String AUDIO_UPLOAD_SUCCESS = """
            {"success":true,"code":"AUDIO_RECEIVED","message":"오디오 파일 수신에 성공했습니다.","data":{"eventId":"evt-audio-20261029-001","filename":"door-knock.wav","size":248320,"eventType":"KNOCK","probability":0.94}}
            """;
    public static final String VIDEO_UPLOAD_SUCCESS = """
            {"success":true,"code":"VIDEO_ANALYZED","message":"영상 분석이 완료되었습니다.","data":{"eventId":"evt-video-20261029-001","filename":"entrance.mp4","vision":{"triggerId":"trigger-keypad-001","triggerType":"KEYPAD","securityEventId":"security-event-001","triggeredAt":"2026-10-29T20:10:15+09:00","analyzedAt":"2026-10-29T20:10:19+09:00","video":{"fileName":"entrance.mp4","durationSeconds":4.2,"fps":30.0,"frameCount":126},"behavior":{"prediction":"A18","label":"문 앞 배회","confidence":0.91,"classProbabilities":{"A18":0.91,"NORMAL":0.09}},"visionEvents":[{"eventType":"PERSON_DWELL_OBSERVED","confidence":0.91,"detectedFrame":83,"details":{"durationSeconds":3.1}}],"observations":{"trackedPersonCount":1,"hasTracking":true,"hasPose":true,"analysisMode":"exhibition_rules"}}}}
            """;
    public static final String AUDIO_RESULT_REQUEST = """
            {"detectionId":"audio-20261029-001","status":"success","predicted_class":"knock","probabilities":{"background":0.02,"knock":0.94,"handle":0.04},"window_start_seconds":1.5,"cooldown_suppressed":false}
            """;
    public static final String AUDIO_RESULT_SUCCESS = """
            {"success":true,"code":"AUDIO_RESULT_RECEIVED","message":"Audio result received.","data":{"detectionId":"audio-20261029-001","eventId":"evt-audio-20261029-001","outcome":"TRIGGERED"}}
            """;
    public static final String TTS_REQUEST = """
            {"message":"문 앞에서 물러나 주세요. 보호자에게 알림이 전송되었습니다."}
            """;
    public static final String TTS_SUCCESS = """
            {"success":true,"code":"TTS_MESSAGE_PUBLISHED","message":"TTS 메시지가 전송되었습니다.","data":null}
            """;
    public static final String DEVICE_TOKEN_REQUEST = """
            {"token":"fcm-device-token-2xYz9KpQ_example","deviceType":"ANDROID"}
            """;
    public static final String DEVICE_TOKEN_SUCCESS = """
            {"success":true,"code":"DEVICE_TOKEN_REGISTERED","message":"디바이스 토큰이 등록되었습니다.","data":null}
            """;
    public static final String NOTIFICATION_SUCCESS = """
            {"success":true,"code":"TEST_NOTIFICATION_SENT","message":"테스트 알림이 전송되었습니다.","data":null}
            """;
    public static final String SECURITY_EVENT_LIST_SUCCESS = """
            {"content":[{"id":41,"eventId":"evt-video-20261029-001","userId":17,"triggerId":"trigger-keypad-001","triggerType":"KEYPAD","status":"ANALYZED","audioEventType":null,"audioProbability":null,"visionPrediction":"A18","visionConfidence":0.91,"visionAnalysisMode":"exhibition_rules","classProbabilities":"{\\"A18\\":0.91,\\"NORMAL\\":0.09}","visionEvents":"[{\\"eventType\\":\\"PERSON_DWELL_OBSERVED\\"}]","personCount":1,"videoDurationSeconds":4.2,"riskLevel":"HIGH","riskScore":82,"agentReason":"문 앞 배회 행동이 감지되었습니다.","triggeredAt":"2026-10-29T20:10:15+09:00","visionAnalyzedAt":"2026-10-29T20:10:19+09:00","createdAt":"2026-10-29T20:10:15","updatedAt":"2026-10-29T20:10:20"}],"page":0,"size":20,"totalElements":1,"totalPages":1,"first":true,"last":true}
            """;
    public static final String SECURITY_EVENT_DETAIL_SUCCESS = """
            {"id":41,"eventId":"evt-video-20261029-001","userId":17,"triggerId":"trigger-keypad-001","triggerType":"KEYPAD","status":"ANALYZED","audioEventType":null,"audioProbability":null,"visionPrediction":"A18","visionConfidence":0.91,"visionAnalysisMode":"exhibition_rules","classProbabilities":"{\\"A18\\":0.91,\\"NORMAL\\":0.09}","visionEvents":"[{\\"eventType\\":\\"PERSON_DWELL_OBSERVED\\"}]","personCount":1,"videoDurationSeconds":4.2,"riskLevel":"HIGH","riskScore":82,"agentReason":"문 앞 배회 행동이 감지되었습니다.","triggeredAt":"2026-10-29T20:10:15+09:00","visionAnalyzedAt":"2026-10-29T20:10:19+09:00","createdAt":"2026-10-29T20:10:15","updatedAt":"2026-10-29T20:10:20"}
            """;
    public static final String VIDEO_URL_SUCCESS = """
            {"url":"https://omniguardy-media.s3.ap-northeast-2.amazonaws.com/videos/evt-video-20261029-001.mp4?X-Amz-Expires=300&X-Amz-Signature=example"}
            """;

    public static final String INVALID_REQUEST = """
            {"success":false,"status":400,"code":"INVALID_REQUEST","message":"잘못된 요청입니다.","data":null}
            """;
    public static final String AUTHENTICATION_REQUIRED = """
            {"success":false,"status":401,"code":"AUTHENTICATION_REQUIRED","message":"인증이 필요합니다.","data":null}
            """;
    public static final String ACCESS_DENIED = """
            {"success":false,"status":403,"code":"ACCESS_DENIED","message":"접근 권한이 없습니다.","data":null}
            """;
    public static final String EMAIL_ALREADY_EXISTS = """
            {"success":false,"status":409,"code":"EMAIL_ALREADY_EXISTS","message":"이미 가입된 이메일입니다.","data":null}
            """;
    public static final String INVALID_LOGIN_INFO = """
            {"success":false,"status":401,"code":"INVALID_LOGIN_INFO","message":"이메일 또는 비밀번호가 올바르지 않습니다.","data":null}
            """;
    public static final String INVALID_TOKEN = """
            {"success":false,"status":401,"code":"INVALID_TOKEN","message":"유효하지 않은 토큰입니다.","data":null}
            """;
    public static final String EXPIRED_TOKEN = """
            {"success":false,"status":401,"code":"EXPIRED_TOKEN","message":"만료된 토큰입니다.","data":null}
            """;
    public static final String REFRESH_TOKEN_NOT_FOUND = """
            {"success":false,"status":401,"code":"REFRESH_TOKEN_NOT_FOUND","message":"Refresh Token이 존재하지 않습니다.","data":null}
            """;
    public static final String REFRESH_TOKEN_MISMATCH = """
            {"success":false,"status":401,"code":"REFRESH_TOKEN_MISMATCH","message":"Refresh Token이 일치하지 않습니다.","data":null}
            """;
    public static final String REFRESH_TOKEN_EXPIRED = """
            {"success":false,"status":401,"code":"REFRESH_TOKEN_EXPIRED","message":"Refresh Token이 만료되었습니다.","data":null}
            """;
    public static final String SECURITY_EVENT_NOT_FOUND = """
            {"success":false,"status":404,"code":"SECURITY_EVENT_NOT_FOUND","message":"보안 이벤트를 찾을 수 없습니다.","data":null}
            """;
    public static final String EMPTY_MEDIA_FILE = """
            {"success":false,"status":400,"code":"MEDIA_FILE_REQUIRED","message":"분석할 미디어 파일은 필수입니다.","data":null}
            """;
    public static final String VISION_API_FAILED = """
            {"success":false,"status":502,"code":"VISION_API_FAILED","message":"영상 AI 서버 처리에 실패했습니다.","data":null}
            """;
    public static final String AUDIO_API_FAILED = """
            {"success":false,"status":502,"code":"AUDIO_API_FAILED","message":"오디오 AI 서버 처리에 실패했습니다.","data":null}
            """;
    public static final String VISION_ANALYSIS_FAILED = """
            {"success":false,"status":502,"code":"VISION_ANALYSIS_FAILED","message":"영상 분석 서버 처리에 실패했습니다.","data":null}
            """;
    public static final String AUDIO_ANALYSIS_FAILED = """
            {"success":false,"status":502,"code":"AUDIO_ANALYSIS_FAILED","message":"오디오 분석 서버 처리에 실패했습니다.","data":null}
            """;
    public static final String MEDIA_STORAGE_FAILED = """
            {"success":false,"status":500,"code":"MEDIA_STORAGE_FAILED","message":"미디어 파일 저장에 실패했습니다.","data":null}
            """;
    public static final String VIDEO_TOO_LARGE = """
            {"success":false,"status":413,"code":"VIDEO_FILE_TOO_LARGE","message":"영상 파일은 100MB 이하여야 합니다.","data":null}
            """;
    public static final String VIDEO_UPLOAD_CONFLICT = """
            {"success":false,"status":409,"code":"VIDEO_UPLOAD_CONFLICT","message":"This trigger is already processing or has different video content.","data":null}
            """;
    public static final String PUSH_DELIVERY_FAILED = """
            {"success":false,"status":502,"code":"PUSH_DELIVERY_FAILED","message":"푸시 알림 전송에 실패했습니다.","data":null}
            """;
    public static final String TTS_DISABLED = """
            {"success":false,"status":503,"code":"TTS_DISABLED","message":"TTS is disabled in this environment.","data":null}
            """;
    public static final String TTS_DELIVERY_FAILED = """
            {"success":false,"status":502,"code":"TTS_DELIVERY_FAILED","message":"TTS 생성 또는 전송에 실패했습니다.","data":null}
            """;
    public static final String INTERNAL_SERVER_ERROR = """
            {"success":false,"status":500,"code":"INTERNAL_SERVER_ERROR","message":"서버 내부 오류가 발생했습니다.","data":null}
            """;
}
