package vinicius.muller.SpringBank.utils;

import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.model.Account;
import vinicius.muller.SpringBank.model.User;
import vinicius.muller.SpringBank.repository.AccountRepository;

public final class AccountUtils {

    // loads the authenticated caller's account
    public static Account callerAccount(AccountRepository accountRepository) {
        User caller = SecurityUtils.authenticatedUser();

        return accountRepository.findByUserId(caller.getId())
                .orElseThrow(() ->
                        new AccountNotFoundException("Account not found for user: " + caller.getId()));
    }
}
