package vinicius.muller.SpringBank.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequestDTO(
        @NotNull
        String senderAccountNumber,

        @NotNull
        @Positive
        @Digits(integer = 13, fraction = 2) // matches DECIMAL(15,2)
        BigDecimal value,

        @NotBlank
        String pin

) {}
