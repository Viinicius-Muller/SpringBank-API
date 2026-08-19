package vinicius.muller.SpringBank.utils;

import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.exception.UnauthorizedTransferException;
import vinicius.muller.SpringBank.model.Account;
import vinicius.muller.SpringBank.model.User;
import vinicius.muller.SpringBank.repository.AccountRepository;

import java.util.Optional;
import java.util.function.Function;

public final class AccountUtils {

    // loads the authenticated caller's account
    public static Account callerAccount(AccountRepository accountRepository, String accountNumber) {
        return ownedAccount(accountRepository::findByAccountNumber, accountNumber);
    }

    // same ownership rules, but takes a PESSIMISTIC_WRITE lock - for balance writers
    public static Account callerAccountForUpdate(AccountRepository accountRepository, String accountNumber) {
        return ownedAccount(accountRepository::findByAccountNumberForUpdate, accountNumber);
    }

    // 404s an unknown number and 403s an account the caller does not own
    private static Account ownedAccount(Function<String, Optional<Account>> finder, String accountNumber) {
        User caller = SecurityUtils.authenticatedUser();

        Account account = finder.apply(accountNumber)
                .orElseThrow(() ->
                        new AccountNotFoundException("Account not found by number: " + accountNumber));

        if (!account.getUser().getId().equals(caller.getId()))
            throw new UnauthorizedTransferException(
                    "User " + caller.getId() + " is not the owner of account " + account.getId());

        return account;
    }
}
