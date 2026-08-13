package vinicius.muller.SpringBank.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCredentialsRequest(
        @NotBlank
        String currentPassword,

        @Size(min = 3, max = 50)
        String newUsername,

        @Email
        @Size(max = 100)
        String newEmail,

        @Size(min = 8, max = 100)
        String newPassword

) {

    @AssertTrue(message = "At least one new credential must be provided")
    public boolean isAnyChangeRequested() {
        return newUsername != null || newEmail != null || newPassword != null;
    }
}
