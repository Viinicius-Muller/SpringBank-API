package vinicius.muller.SpringBank.utils;

import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.exception.UnauthorizedTransferException;
import vinicius.muller.SpringBank.model.Account;
import vinicius.muller.SpringBank.model.User;
import vinicius.muller.SpringBank.repository.AccountRepository;

public final class AccountUtils {

    // loads the authenticated caller's account
    public static Account callerAccount(AccountRepository accountRepository, String accountNumber) {
        User caller = SecurityUtils.authenticatedUser();

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new AccountNotFoundException("Account not found by number: " + accountNumber));

        if (!account.getUser().getId().equals(caller.getId()))
            throw new UnauthorizedTransferException(
                    "User " + caller.getId() + " is not the owner of account " + account.getId());

        return account;
    }
}
