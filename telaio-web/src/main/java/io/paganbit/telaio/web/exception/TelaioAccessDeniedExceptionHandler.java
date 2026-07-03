package io.paganbit.telaio.web.exception;

import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps Spring Security's {@link AccessDeniedException} (including Telaio's own {@code DalAccessDeniedException})
 * to a <strong>generic</strong> {@code 403} RFC 9457 {@link ProblemDetail}, so authorization failures share
 * the same {@code application/problem+json} standard as every other Telaio error.
 *
 * <p>The body is deliberately minimal — {@code status} and {@code title} only, no {@code detail} — per OWASP
 * (A01 Broken Access Control, CWE-209): an authorization failure must not reveal <em>why</em> access was
 * denied, which permission is required, or whether the resource exists. The specific reason (from
 * {@code DalAccessDeniedMessageResolver}) stays on the exception for server-side logging and auditing, and is
 * never written to the response.</p>
 *
 * <p>Registered only when Spring Security is on the classpath (the web module depends on it
 * {@code optional}); see {@code TelaioWebAutoConfiguration}. It is separate advice from
 * {@link TelaioWebExceptionHandler} precisely so the core web handler stays free of any Spring Security type.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
@RestControllerAdvice
@Order(1000)
@Hidden
public class TelaioAccessDeniedExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TelaioAccessDeniedExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        // Keep the specific reason server-side only (the client gets a generic 403). telaio-audit is the
        // durable security trail for denials; this debug line aids local investigation.
        log.debug("Access denied: {}", ex.getMessage());
        return ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    }
}
