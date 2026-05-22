package com.eevee.usageservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps domain/runtime exceptions to HTTP responses. Client {@code message} values are sanitized;
 * original causes are logged server-side only.
 */
@RestControllerAdvice
public class UsageApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UsageApiExceptionHandler.class);

    private static final String ERROR_BAD_REQUEST = "bad_request";

    private static final String ERROR_INTERNAL = "internal_error";

    /** Client-safe text for {@link IllegalArgumentException} (validation / bad input). */
    static final String CLIENT_MESSAGE_BAD_REQUEST =
            "올바르지 않은 요청 형식입니다. 데이터 필터를 다시 확인해 주세요.";

    /** Client-safe text for {@link IllegalStateException} (unexpected business/runtime state). */
    static final String CLIENT_MESSAGE_INTERNAL_ERROR =
            "일시적인 서버 오류가 발생했습니다. 시스템 관리자에게 문의해 주세요. (Code: ERR_BIZ_500)";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        log.warn("Bad request ({}): {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ERROR_BAD_REQUEST, "message", CLIENT_MESSAGE_BAD_REQUEST));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> illegalState(IllegalStateException ex) {
        log.error("Internal state error ({}): {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ERROR_INTERNAL, "message", CLIENT_MESSAGE_INTERNAL_ERROR));
    }
}
