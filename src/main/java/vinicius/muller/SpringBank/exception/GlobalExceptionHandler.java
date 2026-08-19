package vinicius.muller.SpringBank.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
// Spring Data 4 moved this out of org.springframework.data.mapping
import org.springframework.data.core.PropertyReferenceException;
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

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Account not found");
    }

    // Stays vague on purpose, so a wrong password and an unknown e-mail look identical
    @ExceptionHandler({UsernameNotFoundException.class, BadCredentialsException.class,
            IncorrectCredentialsException.class, InvalidAccountCredentialsException.class})
    ProblemDetail handleBadCredentials(Exception ex) {
        log.warn("Failed authentication attempt");
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    // Message names the caller and the owner, so it is logged and not echoed
    @ExceptionHandler(UnauthorizedTransferException.class)
    ProblemDetail handleUnauthorizedTransfer(UnauthorizedTransferException ex) {
        log.warn("Transfer attempt on an account the caller does not own");
        return problem(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(SelfTransferException.class)
    ProblemDetail handleSelfTransfer(SelfTransferException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Cannot transfer to the same account");
    }

    // Deliberately amount-free, so a rejected transfer never discloses the balance
    @ExceptionHandler(InsufficientBalanceException.class)
    ProblemDetail handleInsufficientBalance(InsufficientBalanceException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance");
    }

    @ExceptionHandler(InactiveAccountException.class)
    ProblemDetail handleInactiveAccount(InactiveAccountException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Account is not active");
    }

    // Echoes the message, since a registration form needs to know which field collided
    @ExceptionHandler(AlreadyRegisteredException.class)
    ProblemDetail handleAlreadyRegistered(AlreadyRegisteredException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Every generated number collided - transient, so the client can just retry
    @ExceptionHandler(AccountNumberGenerationException.class)
    ProblemDetail handleAccountNumberGeneration(AccountNumberGenerationException ex) {
        log.error("Reached maximum account number generator attempts");
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Could not allocate an account number, please retry");
    }

    // A bad ?sort= on a paged endpoint is a client error - without this it lands in the catch-all as a 500
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Unknown sort property");
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
