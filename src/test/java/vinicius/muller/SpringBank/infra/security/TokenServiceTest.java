package vinicius.muller.SpringBank.infra.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vinicius.muller.SpringBank.model.Role;
import vinicius.muller.SpringBank.model.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256!!";

    private TokenService tokenService;
    private User user;

    @BeforeEach
    void setUp() {
        tokenService = newTokenService(3600000L);

        user = new User();
        user.setUsername("vinicius");
        user.setEmail("vinicius@springbank.dev");
        user.setPasswordHash("hashed");
        user.setRole(Role.MEMBER);
    }

    private TokenService newTokenService(long expirationMs) {
        TokenService service = new TokenService();
        ReflectionTestUtils.setField(service, "secret", SECRET);
        ReflectionTestUtils.setField(service, "expirationMs", expirationMs);
        ReflectionTestUtils.invokeMethod(service, "initKey");
        return service;
    }

    @Test
    void extractsEmailFromGeneratedToken() {
        String token = tokenService.generateToken(user);

        assertEquals(user.getEmail(), tokenService.extractSubject(token));
    }

    @Test
    void acceptsFreshToken() {
        assertTrue(tokenService.isTokenValid(tokenService.generateToken(user)));
    }

    @Test
    void rejectsTamperedToken() {
        String token = tokenService.generateToken(user);
        // Flip a character in the middle of the signature. The final base64url character only
        // carries padding bits, so flipping *that* one can decode to the same signature and
        // leave the token valid - which made this test intermittently fail.
        int signatureStart = token.lastIndexOf('.') + 1;
        int target = signatureStart + (token.length() - signatureStart) / 2;
        char at = token.charAt(target);
        String tampered = token.substring(0, target) + (at == 'A' ? 'B' : 'A') + token.substring(target + 1);

        assertFalse(tokenService.isTokenValid(tampered));
    }

    @Test
    void rejectsExpiredToken() {
        String expired = newTokenService(-60000L).generateToken(user);

        assertFalse(tokenService.isTokenValid(expired));
    }
}
