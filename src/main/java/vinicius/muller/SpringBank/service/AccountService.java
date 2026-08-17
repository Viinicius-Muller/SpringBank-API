package vinicius.muller.SpringBank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vinicius.muller.SpringBank.dto.AccountResponseDTO;
import vinicius.muller.SpringBank.dto.CreateAccountRequestDTO;
import vinicius.muller.SpringBank.dto.DeleteAccountRequestDTO;
import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.exception.AlreadyRegisteredException;
import vinicius.muller.SpringBank.exception.IncorrectCredentialsException;
import vinicius.muller.SpringBank.utils.AccountNumberGenerator;
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
    private final AccountNumberGenerator accountNumberGenerator;

    @Qualifier("pinEncoder") // use pinEncoder bean instead of default
    private final PasswordEncoder pinEncoder;

    @Transactional
    public AccountResponseDTO createAccount(CreateAccountRequestDTO createDTO) {
        User caller = SecurityUtils.authenticatedUser();

        if (accountRepository.existsByUserId(caller.getId()))
            throw new AlreadyRegisteredException("User already has an Account");

        Account account = new Account();
        account.setUser(caller);
        account.setPinHash(pinEncoder.encode(createDTO.pin()));

        // Try creating account number up to 5 times
        for (int i = 0; i < 5; i++) {
            String generatedAccNumber = accountNumberGenerator.genNumber(caller.getId());
            try {
                account.setAccountNumber(generatedAccNumber);
                accountRepository.save(account);
            } catch (DataIntegrityViolationException ex) {
                // max attempt reached and wasn't successful
                if (i == 4) {
                    log.error("Reached maximum generator attempts");

                    account.setId(null);
                    account.setAccountNumber(null);
                    return null;
                }
                log.error("Not unique value: {}",generatedAccNumber);
            }
        }

        return new AccountResponseDTO(account);
    }

    public AccountResponseDTO getMyAccount() {
        return new AccountResponseDTO(callerAccount());
    }

    @Transactional
    public void deleteAccount(DeleteAccountRequestDTO deleteDTO) {
        Account account = callerAccount();

        if (!account.isPinCorrect(deleteDTO.pin(), pinEncoder))
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
