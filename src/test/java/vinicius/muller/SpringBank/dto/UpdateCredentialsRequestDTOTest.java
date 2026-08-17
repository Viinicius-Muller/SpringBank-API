package vinicius.muller.SpringBank.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateCredentialsRequestDTOTest {

    private static final String CURRENT_PASSWORD = "sup3r-secret";

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
    void acceptsPartialUpdate() {
        var request = new UpdateCredentialsRequestDTO(CURRENT_PASSWORD, null, null, "n3w-password");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsUpdateWithNothingToChange() {
        var request = new UpdateCredentialsRequestDTO(CURRENT_PASSWORD, null, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("anyChangeRequested");
    }

    @Test
    void rejectsBlankCurrentPassword() {
        var request = new UpdateCredentialsRequestDTO("   ", null, null, "n3w-password");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("currentPassword");
    }

    @Test
    void rejectsMalformedNewEmail() {
        var request = new UpdateCredentialsRequestDTO(CURRENT_PASSWORD, null, "not-an-email", null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("newEmail");
    }

    @Test
    void rejectsShortNewPassword() {
        var request = new UpdateCredentialsRequestDTO(CURRENT_PASSWORD, null, null, "1234567");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("newPassword");
    }
}
