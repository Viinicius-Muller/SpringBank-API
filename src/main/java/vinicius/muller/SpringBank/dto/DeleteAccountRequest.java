package vinicius.muller.SpringBank.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank
        String pin

) {}
