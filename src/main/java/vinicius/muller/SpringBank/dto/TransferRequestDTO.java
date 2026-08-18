package vinicius.muller.SpringBank.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequestDTO(
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "Receiver account number must be 6 digits")
        String receiverAccountNumber,

        @NotNull
        @DecimalMin(value = "0.01", message = "Transfer value must be at least 0.01")
        @Digits(integer = 13, fraction = 2) // matches DECIMAL(15,2)
        BigDecimal value,

        @NotBlank
        @Pattern(regexp = "\\d{4,6}", message = "PIN must be 4 to 6 digits")
        String pin

) {}
