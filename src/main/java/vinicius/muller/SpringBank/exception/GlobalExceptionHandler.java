package vinicius.muller.SpringBank.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

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

    // Stays vague on purpose, so a wrong password and an unknown e-mail look identical
    @ExceptionHandler({UsernameNotFoundException.class, BadCredentialsException.class,
            IncorrectCredentialsException.class})
    ProblemDetail handleBadCredentials(Exception ex) {
        log.warn("Failed authentication attempt");
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    // Echoes the message, since a registration form needs to know which field collided
    @ExceptionHandler(AlreadyRegisteredException.class)
    ProblemDetail handleAlreadyRegistered(AlreadyRegisteredException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied");
    }

    // Bean validation failures on @Valid request bodies - without this they fall
    // through to the catch-all below and surface as a 500 instead of a 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidationFailure(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();

        // Field constraints keep their field name; @AssertTrue getters on a record land here too
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.merge(error.getField(), messageOf(error.getDefaultMessage()),
                        (first, second) -> first + "; " + second));

        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.merge(error.getObjectName(), messageOf(error.getDefaultMessage()),
                        (first, second) -> first + "; " + second));

        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Request validation failed");
        detail.setProperty("errors", errors);

        return detail;
    }

    private String messageOf(String defaultMessage) {
        return defaultMessage == null ? "Invalid value" : defaultMessage;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }
}
