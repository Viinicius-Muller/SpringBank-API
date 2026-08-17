package vinicius.muller.SpringBank.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vinicius.muller.SpringBank.dto.UpdateCredentialsRequestDTO;
import vinicius.muller.SpringBank.exception.AlreadyRegisteredException;
import vinicius.muller.SpringBank.exception.IncorrectCredentialsException;
import vinicius.muller.SpringBank.exception.UserNotFoundByEmail;
import vinicius.muller.SpringBank.infra.security.TokenService;
import vinicius.muller.SpringBank.model.User;
import vinicius.muller.SpringBank.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256!!";
    private static final String EMAIL = "vinicius@springbank.dev";
    private static final String USERNAME = "vinicius";
    private static final String PASSWORD = "sup3r-secret";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        TokenService tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
        ReflectionTestUtils.setField(tokenService, "expirationMs", 3600000L);
        ReflectionTestUtils.invokeMethod(tokenService, "initKey");

        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, tokenService, passwordEncoder);

        user = new User();
        user.setId(1L);
        user.setUsername(USERNAME);
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

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
    void updatesPasswordAndReturnsFreshToken() {
        var request = new UpdateCredentialsRequestDTO(PASSWORD, null, null, "n3w-password");

        var response = authService.updateCredentials(request);

        assertThat(passwordEncoder.matches("n3w-password", user.getPasswordHash())).isTrue();
        assertThat(response.token()).isNotBlank();
        verify(userRepository).save(user);
    }

    @Test
    void updatesUsernameAndEmail() {
        when(userRepository.existsByUsername("newname")).thenReturn(false);
        when(userRepository.existsByEmail("new@springbank.dev")).thenReturn(false);
        var request = new UpdateCredentialsRequestDTO(PASSWORD, "newname", "new@springbank.dev", null);

        var response = authService.updateCredentials(request);

        assertThat(user.getUsername()).isEqualTo("newname");
        assertThat(user.getEmail()).isEqualTo("new@springbank.dev");
        assertThat(response.email()).isEqualTo("new@springbank.dev");
    }

    @Test
    void rejectsWrongCurrentPassword() {
        var request = new UpdateCredentialsRequestDTO("wrong-password", null, null, "n3w-password");

        assertThatThrownBy(() -> authService.updateCredentials(request))
                .isInstanceOf(IncorrectCredentialsException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsUsernameTakenByAnotherAccount() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);
        var request = new UpdateCredentialsRequestDTO(PASSWORD, "taken", null, null);

        assertThatThrownBy(() -> authService.updateCredentials(request))
                .isInstanceOf(AlreadyRegisteredException.class);
    }

    @Test
    void rejectsEmailTakenByAnotherAccount() {
        when(userRepository.existsByEmail("taken@springbank.dev")).thenReturn(true);
        var request = new UpdateCredentialsRequestDTO(PASSWORD, null, "taken@springbank.dev", null);

        assertThatThrownBy(() -> authService.updateCredentials(request))
                .isInstanceOf(AlreadyRegisteredException.class);
    }

    // Resubmitting your own unchanged values must not collide with yourself
    @Test
    void allowsResubmittingOwnUsernameAndEmail() {
        var request = new UpdateCredentialsRequestDTO(PASSWORD, USERNAME, EMAIL, "n3w-password");

        authService.updateCredentials(request);

        verify(userRepository, never()).existsByUsername(anyString());
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    void rejectsCallWithoutAuthentication() {
        SecurityContextHolder.clearContext();
        var request = new UpdateCredentialsRequestDTO(PASSWORD, null, null, "n3w-password");

        assertThatThrownBy(() -> authService.updateCredentials(request))
                .isInstanceOf(IncorrectCredentialsException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsAnonymousCaller() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        var request = new UpdateCredentialsRequestDTO(PASSWORD, null, null, "n3w-password");

        assertThatThrownBy(() -> authService.updateCredentials(request))
                .isInstanceOf(IncorrectCredentialsException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    // The account comes from the token, so naming someone else in the body changes nothing
    @Test
    void cannotTargetAnotherAccountThroughTheRequest() {
        when(userRepository.existsByEmail("victim@springbank.dev")).thenReturn(false);
        var request = new UpdateCredentialsRequestDTO(PASSWORD, null, "victim@springbank.dev", null);

        authService.updateCredentials(request);

        verify(userRepository).findByEmail(EMAIL);
        verify(userRepository, never()).findByEmail("victim@springbank.dev");
    }

    @Test
    void rejectsCallerMissingFromTheDatabase() {
        User ghost = new User();
        ghost.setEmail("ghost@springbank.dev");
        ghost.setUsername("ghost");
        ghost.setPasswordHash(passwordEncoder.encode(PASSWORD));
        authenticateAs(ghost);

        when(userRepository.findByEmail("ghost@springbank.dev")).thenReturn(Optional.empty());
        var request = new UpdateCredentialsRequestDTO(PASSWORD, null, null, "n3w-password");

        assertThatThrownBy(() -> authService.updateCredentials(request))
                .isInstanceOf(UserNotFoundByEmail.class);
    }
}
