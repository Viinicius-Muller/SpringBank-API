package vinicius.muller.SpringBank.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequestDTO(
        @NotBlank
        String pin

) {}
