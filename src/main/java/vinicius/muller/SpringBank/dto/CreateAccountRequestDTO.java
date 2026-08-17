package vinicius.muller.SpringBank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccountRequestDTO(
        @NotBlank
        @Size(min = 6, max = 6, message = "PIN value must be 6 digits")
        String pin

) {}
