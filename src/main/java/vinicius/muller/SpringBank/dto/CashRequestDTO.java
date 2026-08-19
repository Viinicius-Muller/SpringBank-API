package vinicius.muller.SpringBank.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

// serves both deposit and withdraw
public record CashRequestDTO(
        @NotNull
        @DecimalMin(value = "0.01", message = "Value must be at least 0.01")
        @Digits(integer = 13, fraction = 2) // matches DECIMAL(15,2)
        BigDecimal value,

        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "PIN must be exactly 6 digits")
        String pin

) {}
