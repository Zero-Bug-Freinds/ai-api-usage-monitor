package com.eevee.billingservice.api;

import com.eevee.billingservice.config.BillingProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps persistence failures to HTTP 503 so clients can distinguish infrastructure errors from
 * application bugs. Full causes are logged server-side; optional {@code hint} is controlled by
 * {@link BillingProperties#getError()}{@code .isExposeDatasourceFailureHint()}.
 */
@RestControllerAdvice
public class BillingRestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BillingRestExceptionHandler.class);

    private static final String ERROR_CODE = "DATABASE_UNAVAILABLE";

    private final BillingProperties billingProperties;

    public BillingRestExceptionHandler(BillingProperties billingProperties) {
        this.billingProperties = billingProperties;
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(
            DataAccessException ex,
            HttpServletRequest request
    ) {
        String correlation = firstHeader(request, "X-Correlation-Id", "X-Request-Id");
        log.error(
                "Data access failure [{}] {} {} — {}",
                correlation,
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                ex
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ERROR_CODE);
        body.put("message", "Database access failed");
        if (billingProperties.getError().isExposeDatasourceFailureHint()) {
            body.put(
                    "hint",
                    "Check postgres-billing (default host port 5435, database billing_db), "
                            + "BILLING_POSTGRES_* alignment with docker/postgres init and application.yml, "
                            + "pool exhaustion, and schema/JPA compatibility. "
                            + "See docs/billing-service-overview-20260412.md §6.4."
            );
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private static String firstHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String v = request.getHeader(name);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }
}
