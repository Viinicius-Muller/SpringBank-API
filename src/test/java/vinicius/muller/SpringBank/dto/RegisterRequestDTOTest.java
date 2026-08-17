package vinicius.muller.SpringBank.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestDTOTest {

    private static final String USERNAME = "vinicius";
    private static final String EMAIL = "vinicius@springbank.dev";
    private static final String PASSWORD = "sup3r-secret";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void acceptsValidPayload() {
        var request = new RegisterRequestDTO(USERNAME, EMAIL, PASSWORD);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsBlankUsername() {
        var request = new RegisterRequestDTO("   ", EMAIL, PASSWORD);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("username");
    }

    @Test
    void rejectsMalformedEmail() {
        var request = new RegisterRequestDTO(USERNAME, "not-an-email", PASSWORD);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("email");
    }

    @Test
    void rejectsShortPassword() {
        var request = new RegisterRequestDTO(USERNAME, EMAIL, "1234567");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("password");
    }

    @Test
    void rejectsUsernameLongerThanColumn() {
        var request = new RegisterRequestDTO("v".repeat(51), EMAIL, PASSWORD);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("username");
    }
}
