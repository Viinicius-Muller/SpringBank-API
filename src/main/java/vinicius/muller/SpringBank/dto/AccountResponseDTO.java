package vinicius.muller.SpringBank.dto;

import vinicius.muller.SpringBank.model.Account;

import java.math.BigDecimal;

public record AccountResponseDTO(Long id, String accountNumber, String username, String email,
                                 BigDecimal balance, Boolean active) {

    public AccountResponseDTO(Account account) {
        this(account.getId(),
                account.getAccountNumber(),
                account.getUser().getUsername(),
                account.getUser().getEmail(),
                account.getBalance(),
                account.getActive());
    }
}
