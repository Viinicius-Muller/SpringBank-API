package vinicius.muller.SpringBank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vinicius.muller.SpringBank.dto.AccountResponse;
import vinicius.muller.SpringBank.dto.CreateAccountRequest;
import vinicius.muller.SpringBank.dto.DeleteAccountRequest;
import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.exception.AlreadyRegisteredException;
import vinicius.muller.SpringBank.exception.IncorrectCredentialsException;
import vinicius.muller.SpringBank.utils.SecurityUtils;
import vinicius.muller.SpringBank.model.Account;
import vinicius.muller.SpringBank.model.User;
import vinicius.muller.SpringBank.repository.AccountRepository;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AccountService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest createDTO) {
        User caller = SecurityUtils.authenticatedUser();

        if (accountRepository.existsByUserId(caller.getId()))
            throw new AlreadyRegisteredException("User already has an Account");

        Account account = new Account();
        account.setUser(caller);
        account.setPinHash(passwordEncoder.encode(createDTO.pin()));

        accountRepository.save(account);
        return new AccountResponse(account);
    }

    public AccountResponse getMyAccount() {
        return new AccountResponse(callerAccount());
    }

    @Transactional
    public void deleteAccount(DeleteAccountRequest deleteDTO) {
        Account account = callerAccount();

        if (!account.isPinCorrect(deleteDTO.pin(), passwordEncoder))
            throw new IncorrectCredentialsException("PIN is incorrect");

        account.setActive(false);
        accountRepository.save(account);

        log.info("Account {} deactivated", account.getId());
    }

    private Account callerAccount() {
        User caller = SecurityUtils.authenticatedUser();

        return accountRepository.findByUserId(caller.getId())
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                "Account not found for user: " + caller.getId()
                        )
                );
    }
}
