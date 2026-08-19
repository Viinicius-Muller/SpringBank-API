package vinicius.muller.SpringBank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequestDTO(
        @NotBlank
        @Pattern(regexp = "\\d{4,6}", message = "PIN must be 4 to 6 digits")
        String pin

) {}
