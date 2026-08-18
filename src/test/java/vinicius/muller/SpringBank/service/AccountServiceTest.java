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
import vinicius.muller.SpringBank.dto.CreateAccountRequestDTO;
import vinicius.muller.SpringBank.dto.DeleteAccountRequestDTO;
import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.exception.AlreadyRegisteredException;
import vinicius.muller.SpringBank.exception.IncorrectCredentialsException;
import vinicius.muller.SpringBank.model.Account;
import vinicius.muller.SpringBank.model.User;
import vinicius.muller.SpringBank.repository.AccountRepository;
import vinicius.muller.SpringBank.utils.AccountNumberGenerator;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private static final String EMAIL = "vinicius@springbank.dev";
    private static final String USERNAME = "vinicius";
    private static final String PIN = "4821";

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
        account.setUser(user);
        account.setPinHash(passwordEncoder.encode(PIN));

        when(accountRepository.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));

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

    @Test
    void createsAccountWithEncodedPinAndZeroBalance() {
        when(accountRepository.existsByUserId(user.getId())).thenReturn(false);

        var response = accountService.createAccount(new CreateAccountRequestDTO(PIN));

        assertThat(response.username()).isEqualTo(USERNAME);
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.active()).isTrue();
    }

    @Test
    void neverStoresPinInPlainText() {
        when(accountRepository.existsByUserId(user.getId())).thenReturn(false);

        accountService.createAccount(new CreateAccountRequestDTO(PIN));

        var saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());

        assertThat(saved.getValue().getPinHash()).isNotEqualTo(PIN);
        assertThat(passwordEncoder.matches(PIN, saved.getValue().getPinHash())).isTrue();
    }

    @Test
    void rejectsSecondAccountForSameUser() {
        when(accountRepository.existsByUserId(user.getId())).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(new CreateAccountRequestDTO(PIN)))
                .isInstanceOf(AlreadyRegisteredException.class);

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void returnsCallerAccount() {
        when(accountRepository.findByUserId(user.getId())).thenReturn(Optional.of(account));

        var response = accountService.getMyAccount();

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.username()).isEqualTo(USERNAME);
    }

    @Test
    void rejectsReadWhenCallerHasNoAccount() {
        when(accountRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getMyAccount())
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void deactivatesAccountOnCorrectPin() {
        when(accountRepository.findByUserId(user.getId())).thenReturn(Optional.of(account));

        accountService.deleteAccount(new DeleteAccountRequestDTO(PIN));

        assertThat(account.getActive()).isFalse();
        verify(accountRepository).save(account);
    }

    @Test
    void rejectsDeleteOnWrongPin() {
        when(accountRepository.findByUserId(user.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.deleteAccount(new DeleteAccountRequestDTO("0000")))
                .isInstanceOf(IncorrectCredentialsException.class);

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

        assertThatThrownBy(() -> accountService.getMyAccount())
                .isInstanceOf(IncorrectCredentialsException.class);
    }
}
