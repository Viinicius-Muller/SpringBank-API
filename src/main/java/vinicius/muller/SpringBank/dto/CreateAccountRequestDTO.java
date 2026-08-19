package vinicius.muller.SpringBank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequestDTO(
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "PIN must be exactly 6 digits")
        String pin

) {}
