package vinicius.muller.SpringBank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vinicius.muller.SpringBank.dto.AccountResponseDTO;
import vinicius.muller.SpringBank.dto.CashRequestDTO;
import vinicius.muller.SpringBank.dto.CreateAccountRequestDTO;
import vinicius.muller.SpringBank.dto.DeleteAccountRequestDTO;
import vinicius.muller.SpringBank.exception.AccountNumberGenerationException;
import vinicius.muller.SpringBank.exception.InactiveAccountException;
import vinicius.muller.SpringBank.exception.IncorrectCredentialsException;
import vinicius.muller.SpringBank.exception.InsufficientBalanceException;
import vinicius.muller.SpringBank.exception.InvalidAccountCredentialsException;
import vinicius.muller.SpringBank.utils.AccountNumberGenerator;
import vinicius.muller.SpringBank.utils.AccountUtils;
import vinicius.muller.SpringBank.utils.SecurityUtils;
import vinicius.muller.SpringBank.model.Account;
import vinicius.muller.SpringBank.model.User;
import vinicius.muller.SpringBank.repository.AccountRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AccountService {
    private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 10;

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    @Qualifier("pinEncoder") // use pinEncoder bean instead of default
    private final PasswordEncoder pinEncoder;

    @Transactional
    public AccountResponseDTO createAccount(CreateAccountRequestDTO createDTO) {
        User caller = SecurityUtils.authenticatedUser();

        Account account = new Account();
        account.setUser(caller);
        account.setPinHash(pinEncoder.encode(createDTO.pin()));

        account.setAccountNumber(freeAccountNumber());
        accountRepository.saveAndFlush(account);

        log.info("Account {} created for user {}", account.getId(), caller.getId());
        return new AccountResponseDTO(account);
    }

    public List<AccountResponseDTO> getMyAccounts() {
        User caller = SecurityUtils.authenticatedUser();

        return accountRepository.findByUserId(caller.getId()).stream()
                .map(AccountResponseDTO::new)
                .toList();
    }

    public AccountResponseDTO getMyAccount(String accountNumber) {
        return new AccountResponseDTO(AccountUtils.callerAccount(accountRepository, accountNumber));
    }

    @Transactional
    public void deleteAccount(DeleteAccountRequestDTO deleteDTO, String accountNumber) {
        Account account = AccountUtils.callerAccount(accountRepository, accountNumber);

        if (!account.isPinCorrect(deleteDTO.pin(), pinEncoder))
            throw new IncorrectCredentialsException("PIN is incorrect");

        account.setActive(false);
        accountRepository.save(account);

        log.info("Account {} deactivated", account.getId());
    }

    @Transactional
    public AccountResponseDTO deposit(CashRequestDTO cashDTO, String accountNumber) {
        Account account = verifiedAccountForUpdate(cashDTO, accountNumber);

        account.setBalance(account.getBalance().add(cashDTO.value()));

        log.info("Deposited {} into account {}", cashDTO.value(), account.getId());

        // balance is flushed on commit
        return new AccountResponseDTO(account);
    }

    @Transactional
    public AccountResponseDTO withdraw(CashRequestDTO cashDTO, String accountNumber) {
        Account account = verifiedAccountForUpdate(cashDTO, accountNumber);

        if (account.getBalance().compareTo(cashDTO.value()) < 0)
            throw new InsufficientBalanceException("Not enough balance to withdraw");

        account.setBalance(account.getBalance().subtract(cashDTO.value()));

        log.info("Withdrew {} from account {}", cashDTO.value(), account.getId());

        return new AccountResponseDTO(account);
    }

    // locks row and validates
    private Account verifiedAccountForUpdate(CashRequestDTO cashDTO, String accountNumber) {

        if (cashDTO.value() == null || cashDTO.value().signum() <= 0)
            throw new IllegalArgumentException("Value must be positive");

        Account account = AccountUtils.callerAccountForUpdate(accountRepository, accountNumber);

        if (!account.isPinCorrect(cashDTO.pin(), pinEncoder))
            throw new InvalidAccountCredentialsException("Failed to validate Account credentials");

        if (!account.getActive())
            throw new InactiveAccountException("Account is not active");

        return account;
    }

    private String freeAccountNumber() {
        for (int i = 0; i < MAX_ACCOUNT_NUMBER_ATTEMPTS; i++) {
            String generatedAccNumber = accountNumberGenerator.genNumber();

            if (!accountRepository.existsByAccountNumber(generatedAccNumber))
                return generatedAccNumber;

            log.warn("Not unique value: {}", generatedAccNumber);
        }

        throw new AccountNumberGenerationException(
                "Could not generate a free account number in " + MAX_ACCOUNT_NUMBER_ATTEMPTS + " attempts");
    }
}
