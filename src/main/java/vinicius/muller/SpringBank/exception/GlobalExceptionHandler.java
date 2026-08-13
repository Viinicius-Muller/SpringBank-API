package vinicius.muller.SpringBank.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Translates domain exceptions into API responses.
// Note: this only covers what controllers and services throw - exceptions raised inside a
// servlet filter never reach here, which is why SecurityFilter resolves failures itself.
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InactiveLoginException.class)
    ProblemDetail handleInactiveLogin(InactiveLoginException ex) {
        log.warn("Login attempt on an inactive account");
        return problem(HttpStatus.FORBIDDEN, "Account is not active");
    }

    @ExceptionHandler(UserNotFoundByEmail.class)
    ProblemDetail handleUserNotFound(UserNotFoundByEmail ex) {
        return problem(HttpStatus.NOT_FOUND, "User not found");
    }

    // Credential failures stay deliberately vague so they can't be used to enumerate accounts
    @ExceptionHandler({UsernameNotFoundException.class, BadCredentialsException.class})
    ProblemDetail handleBadCredentials(Exception ex) {
        log.warn("Failed authentication attempt");
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    // Must be declared explicitly, otherwise the catch-all below turns a 403 into a 500
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
