package vinicius.muller.SpringBank.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

// covers what controllers and services throw - exceptions
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    //  Spring ProblemDetail builder
    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }

    @ExceptionHandler(InactiveLoginException.class)
    ProblemDetail handleInactiveLogin(InactiveLoginException ex) {
        log.warn("Login attempt on an inactive account");
        return problem(HttpStatus.FORBIDDEN, "Account is not active");
    }

    @ExceptionHandler(UserNotFoundByEmail.class)
    ProblemDetail handleUserNotFound(UserNotFoundByEmail ex) {
        return problem(HttpStatus.NOT_FOUND, "User not found");
    }

    @ExceptionHandler({UsernameNotFoundException.class, BadCredentialsException.class})
    ProblemDetail handleBadCredentials(Exception ex) {
        log.warn("Failed authentication attempt");
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }
}
