package vinicius.muller.SpringBank.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CreateAccountRequestDTOTest {

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

    @ParameterizedTest
    @ValueSource(strings = {"1234", "12345", "123456"})
    void acceptsPinBetweenFourAndSixDigits(String pin) {
        var request = new CreateAccountRequestDTO(pin);

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "1234567", "12a4", "12 4", ""})
    void rejectsMalformedPin(String pin) {
        var request = new CreateAccountRequestDTO(pin);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("pin");
    }

    @Test
    void rejectsBlankPin() {
        var request = new CreateAccountRequestDTO("    ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("pin");
    }

    @Test
    void rejectsNullPin() {
        var request = new CreateAccountRequestDTO(null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("pin");
    }
}
