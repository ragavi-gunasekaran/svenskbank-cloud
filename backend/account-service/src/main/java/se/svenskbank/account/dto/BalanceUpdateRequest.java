package se.svenskbank.account.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Positive amount = money in, negative amount = money out. */
public record BalanceUpdateRequest(
        @NotNull @Digits(integer = 13, fraction = 2) BigDecimal amount
) {
}
