package vinicius.muller.SpringBank.exception;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.MockHttpInputMessage;
import vinicius.muller.SpringBank.dto.RegisterRequestDTO;
import vinicius.muller.SpringBank.dto.UpdateCredentialsRequestDTO;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static SpringValidatorAdapter validatorAdapter;
    private static ValidatorFactory factory;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        validatorAdapter = new SpringValidatorAdapter(validator);
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void reportsFieldErrorsAsBadRequest() {
        var invalid = new RegisterRequestDTO("  ", "not-an-email", "short");

        ProblemDetail detail = handler.handleValidationFailure(exceptionFor(invalid));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(detail.getDetail()).isEqualTo("Request validation failed");
        assertThat(errorsOf(detail)).containsOnlyKeys("username", "email", "password");
    }

    @Test
    void reportsAssertTrueGetterViolation() {
        var invalid = new UpdateCredentialsRequestDTO("current-password", null, null, null);

        ProblemDetail detail = handler.handleValidationFailure(exceptionFor(invalid));

        assertThat(errorsOf(detail))
                .containsEntry("anyChangeRequested", "At least one new credential must be provided");
    }

    @Test
    void mergesMultipleViolationsOnTheSameField() {
        var invalid = new RegisterRequestDTO("valid-name", "  ", "valid-password");

        ProblemDetail detail = handler.handleValidationFailure(exceptionFor(invalid));

        assertThat(errorsOf(detail).get("email")).contains(";");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> errorsOf(ProblemDetail detail) {
        return (Map<String, String>) detail.getProperties().get("errors");
    }

    private MethodArgumentNotValidException exceptionFor(Object target) {
        var binding = new BeanPropertyBindingResult(target, "request");
        validatorAdapter.validate(target, binding);

        assertThat(binding.hasErrors())
                .as("test fixture should produce violations")
                .isTrue();

        return new MethodArgumentNotValidException(parameterStub(), binding);
    }

    private MethodParameter parameterStub() {
        try {
            return new MethodParameter(getClass().getDeclaredMethod("stub", Object.class), 0);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unused")
    private void stub(Object body) {
    }

    @Test
    void reportsMalformedBodyAsBadRequest() {
        ProblemDetail detail = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("unexpected end of input",
                        new MockHttpInputMessage(new byte[0])));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(detail.getDetail()).isEqualTo("Malformed request body");
    }

    @Test
    void reportsUnknownRouteAsNotFound() {
        ProblemDetail detail = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/nope", "/nope"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void reportsWrongMethodAsMethodNotAllowed() {
        ProblemDetail detail = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
    }

    @Test
    void reportsDataIntegrityViolationAsConflictWithoutLeakingTheCause() {
        ProblemDetail detail = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key value violates unique constraint users_email_key"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(detail.getDetail()).doesNotContain("users_email_key");
    }
}
