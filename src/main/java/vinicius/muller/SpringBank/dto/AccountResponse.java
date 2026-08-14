package vinicius.muller.SpringBank.dto;

import vinicius.muller.SpringBank.model.Account;

import java.math.BigDecimal;

public record AccountResponse(Long id, String username, String email, BigDecimal balance, Boolean active) {

    public AccountResponse(Account account) {
        this(account.getId(),
                account.getUser().getUsername(),
                account.getUser().getEmail(),
                account.getBalance(),
                account.getActive());
    }
}
