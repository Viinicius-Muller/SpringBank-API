package vinicius.muller.SpringBank.infra.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import vinicius.muller.SpringBank.repository.UserRepository;
import vinicius.muller.SpringBank.model.Role;
import vinicius.muller.SpringBank.model.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityFilterTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256!!";
    private static final String EMAIL = "vinicius@springbank.dev";

    private TokenService tokenService;
    private UserRepository userRepository;
    private SecurityFilter filter;
    private User user;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
        ReflectionTestUtils.setField(tokenService, "expirationMs", 3600000L);
        ReflectionTestUtils.invokeMethod(tokenService, "initKey");

        userRepository = mock(UserRepository.class);
        filter = new SecurityFilter(tokenService, userRepository);

        user = new User();
        user.setId(1L);
        user.setUsername("vinicius");
        user.setEmail(EMAIL);
        user.setPasswordHash("hashed");
        user.setRole(Role.MEMBER);
        user.setEnabled(true);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesUserFromValidToken() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        request.addHeader("Authorization", "Bearer " + tokenService.generateToken(user));

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(user);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_MEMBER");
        assertThat(chain.getRequest()).as("chain must continue").isNotNull();
    }

    @Test
    void continuesChainAnonymouslyWithoutAuthorizationHeader() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("chain must continue").isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresHeaderWithoutBearerPrefix() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void continuesChainOnMalformedToken() throws Exception {
        request.addHeader("Authorization", "Bearer not-a-jwt");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("a bad token must not blow up the request").isNotNull();
    }

    @Test
    void continuesChainOnExpiredToken() throws Exception {
        TokenService expiredIssuer = new TokenService();
        ReflectionTestUtils.setField(expiredIssuer, "secret", SECRET);
        ReflectionTestUtils.setField(expiredIssuer, "expirationMs", -60000L);
        ReflectionTestUtils.invokeMethod(expiredIssuer, "initKey");

        request.addHeader("Authorization", "Bearer " + expiredIssuer.generateToken(user));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doesNotAuthenticateUnknownUser() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        request.addHeader("Authorization", "Bearer " + tokenService.generateToken(user));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doesNotAuthenticateDisabledUser() throws Exception {
        user.setEnabled(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        request.addHeader("Authorization", "Bearer " + tokenService.generateToken(user));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
