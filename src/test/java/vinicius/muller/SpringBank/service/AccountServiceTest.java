package vinicius.muller.SpringBank.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vinicius.muller.SpringBank.dto.CashRequestDTO;
import vinicius.muller.SpringBank.dto.CreateAccountRequestDTO;
import vinicius.muller.SpringBank.dto.DeleteAccountRequestDTO;
import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.exception.AccountNumberGenerationException;
import vinicius.muller.SpringBank.exception.InactiveAccountException;
import vinicius.muller.SpringBank.exception.IncorrectCredentialsException;
import vinicius.muller.SpringBank.exception.InsufficientBalanceException;
import vinicius.muller.SpringBank.exception.InvalidAccountCredentialsException;
import vinicius.muller.SpringBank.exception.UnauthorizedTransferException;
import vinicius.muller.SpringBank.model.Account;
import vinicius.muller.SpringBank.model.User;
import vinicius.muller.SpringBank.repository.AccountRepository;
import vinicius.muller.SpringBank.utils.AccountNumberGenerator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private static final String EMAIL = "vinicius@springbank.dev";
    private static final String USERNAME = "vinicius";
    private static final String PIN = "482193";
    private static final String ACCOUNT_NUMBER = "100001";

    private AccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;
    private AccountService accountService;
    private User user;
    private Account account;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        AccountNumberGenerator accountNumberGen = new AccountNumberGenerator();
        accountService = new AccountService(accountRepository, accountNumberGen, passwordEncoder);

        user = new User();
        user.setId(1L);
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);

        account = new Account();
        account.setId(10L);
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setUser(user);
        account.setPinHash(passwordEncoder.encode(PIN));

        when(accountRepository.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        authenticateAs(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private User otherUser() {
        User other = new User();
        other.setId(2L);
        return other;
    }

    @Test
    void createsMultipleAccountsForTheSameUser() {
        var response1 = accountService.createAccount(new CreateAccountRequestDTO(PIN));
        var response2 = accountService.createAccount(new CreateAccountRequestDTO(PIN));

        assertThat(response1.accountNumber()).isNotNull();
        assertThat(response2.accountNumber()).isNotNull();
        verify(accountRepository, times(2)).saveAndFlush(any(Account.class));
    }

    @Test
    void createsAccountWithEncodedPinAndZeroBalance() {
        var response = accountService.createAccount(new CreateAccountRequestDTO(PIN));

        assertThat(response.username()).isEqualTo(USERNAME);
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.accountNumber()).hasSize(6);
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.active()).isTrue();
    }

    // the number is deliberately not user-prefixed - it draws from the full 6-digit space
    @Test
    void createsSixDigitAccountNumberIndependentOfUserId() {
        authenticateAs(otherUser());

        var response = accountService.createAccount(new CreateAccountRequestDTO(PIN));

        assertThat(response.accountNumber()).matches("[0-9]{6}");
    }

    @Test
    void neverStoresPinInPlainText() {
        accountService.createAccount(new CreateAccountRequestDTO(PIN));

        var saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(saved.capture());

        assertThat(saved.getValue().getPinHash()).isNotEqualTo(PIN);
        assertThat(passwordEncoder.matches(PIN, saved.getValue().getPinHash())).isTrue();
    }

    // the retry loop used to keep saving after it had already succeeded
    @Test
    void savesOnlyOncePerCreatedAccount() {
        accountService.createAccount(new CreateAccountRequestDTO(PIN));

        verify(accountRepository).saveAndFlush(any(Account.class));
    }

    // A taken number is skipped before the insert. Catching the unique violation instead
    // could never work: on Postgres it aborts the transaction, so the retry save fails too.
    @Test
    void skipsGeneratedAccountNumberThatIsAlreadyTaken() {
        when(accountRepository.existsByAccountNumber(anyString()))
                .thenReturn(true)
                .thenReturn(false);

        var response = accountService.createAccount(new CreateAccountRequestDTO(PIN));

        assertThat(response.accountNumber()).hasSize(6);
        verify(accountRepository, times(2)).existsByAccountNumber(anyString());
        verify(accountRepository).saveAndFlush(any(Account.class));
    }

    @Test
    void failsAfterExhaustingAccountNumberAttempts() {
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(new CreateAccountRequestDTO(PIN)))
                .isInstanceOf(AccountNumberGenerationException.class);

        verify(accountRepository, times(10)).existsByAccountNumber(anyString());
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void listsEveryAccountOfTheCaller() {
        Account second = new Account();
        second.setId(11L);
        second.setAccountNumber("200002");
        second.setUser(user);

        when(accountRepository.findByUserId(user.getId())).thenReturn(List.of(account, second));

        var response = accountService.getMyAccounts();

        assertThat(response).extracting("accountNumber").containsExactly(ACCOUNT_NUMBER, "200002");
    }

    @Test
    void returnsCallerAccount() {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        var response = accountService.getMyAccount(ACCOUNT_NUMBER);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(response.username()).isEqualTo(USERNAME);
    }

    @Test
    void rejectsReadOfUnknownAccount() {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getMyAccount(ACCOUNT_NUMBER))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void rejectsReadOfAccountOwnedByAnotherUser() {
        account.setUser(otherUser());

        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.getMyAccount(ACCOUNT_NUMBER))
                .isInstanceOf(UnauthorizedTransferException.class);
    }

    @Test
    void deactivatesAccountOnCorrectPin() {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        accountService.deleteAccount(new DeleteAccountRequestDTO(PIN), ACCOUNT_NUMBER);

        assertThat(account.getActive()).isFalse();
        verify(accountRepository).save(account);
    }

    @Test
    void rejectsDeleteOnWrongPin() {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.deleteAccount(new DeleteAccountRequestDTO("000000"), ACCOUNT_NUMBER))
                .isInstanceOf(IncorrectCredentialsException.class);

        assertThat(account.getActive()).isTrue();
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void rejectsDeleteOfAccountOwnedByAnotherUser() {
        account.setUser(otherUser());

        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.deleteAccount(new DeleteAccountRequestDTO(PIN), ACCOUNT_NUMBER))
                .isInstanceOf(UnauthorizedTransferException.class);

        assertThat(account.getActive()).isTrue();
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void rejectsAnonymousCaller() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(() -> accountService.createAccount(new CreateAccountRequestDTO(PIN)))
                .isInstanceOf(IncorrectCredentialsException.class);

        assertThatThrownBy(() -> accountService.getMyAccounts())
                .isInstanceOf(IncorrectCredentialsException.class);

        assertThatThrownBy(() -> accountService.getMyAccount(ACCOUNT_NUMBER))
                .isInstanceOf(IncorrectCredentialsException.class);
    }

    // --- deposit / withdraw: the only path that puts money into an account ---

    private void lockable() {
        when(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));
    }

    @Test
    void depositCreditsTheBalance() {
        account.setBalance(new BigDecimal("10.00"));
        lockable();

        var response = accountService.deposit(new CashRequestDTO(new BigDecimal("90.00"), PIN), ACCOUNT_NUMBER);

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
        assertThat(response.balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void withdrawDebitsTheBalance() {
        account.setBalance(new BigDecimal("100.00"));
        lockable();

        var response = accountService.withdraw(new CashRequestDTO(new BigDecimal("40.00"), PIN), ACCOUNT_NUMBER);

        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
        assertThat(response.balance()).isEqualByComparingTo("60.00");
    }

    @Test
    void withdrawAllowsTheExactFullBalance() {
        account.setBalance(new BigDecimal("100.00"));
        lockable();

        accountService.withdraw(new CashRequestDTO(new BigDecimal("100.00"), PIN), ACCOUNT_NUMBER);

        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void withdrawRejectsMoreThanTheBalance() {
        account.setBalance(new BigDecimal("100.00"));
        lockable();

        assertThatThrownBy(() -> accountService.withdraw(
                new CashRequestDTO(new BigDecimal("100.01"), PIN), ACCOUNT_NUMBER))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void cashOperationsRejectWrongPin() {
        account.setBalance(new BigDecimal("100.00"));
        lockable();

        assertThatThrownBy(() -> accountService.deposit(
                new CashRequestDTO(new BigDecimal("10.00"), "000000"), ACCOUNT_NUMBER))
                .isInstanceOf(InvalidAccountCredentialsException.class);

        assertThatThrownBy(() -> accountService.withdraw(
                new CashRequestDTO(new BigDecimal("10.00"), "000000"), ACCOUNT_NUMBER))
                .isInstanceOf(InvalidAccountCredentialsException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void cashOperationsRejectInactiveAccount() {
        account.setBalance(new BigDecimal("100.00"));
        account.setActive(false);
        lockable();

        assertThatThrownBy(() -> accountService.deposit(
                new CashRequestDTO(new BigDecimal("10.00"), PIN), ACCOUNT_NUMBER))
                .isInstanceOf(InactiveAccountException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void cashOperationsRejectAccountOwnedByAnotherUser() {
        account.setBalance(new BigDecimal("100.00"));
        account.setUser(otherUser());
        lockable();

        assertThatThrownBy(() -> accountService.deposit(
                new CashRequestDTO(new BigDecimal("10.00"), PIN), ACCOUNT_NUMBER))
                .isInstanceOf(UnauthorizedTransferException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void cashOperations404UnknownAccount() {
        when(accountRepository.findByAccountNumberForUpdate("999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deposit(
                new CashRequestDTO(new BigDecimal("10.00"), PIN), "999999"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // must take the row lock, otherwise two concurrent writers could both read the old balance
    @Test
    void cashOperationsLockTheAccountRow() {
        account.setBalance(new BigDecimal("100.00"));
        lockable();

        accountService.deposit(new CashRequestDTO(new BigDecimal("10.00"), PIN), ACCOUNT_NUMBER);

        verify(accountRepository).findByAccountNumberForUpdate(ACCOUNT_NUMBER);
        verify(accountRepository, never()).findByAccountNumber(anyString());
    }
}
