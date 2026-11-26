package se.svenskbank.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Size(max = 100) String ownerName,
        @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal initialDeposit
) {
}
