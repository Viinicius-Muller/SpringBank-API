package vinicius.muller.SpringBank.infra.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomPinEncoderTest {

    private static final String PEPPER = "test-pepper-not-the-one-used-in-production";
    private static final String PIN = "482193";

    private CustomPinEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = newEncoder(PEPPER);
    }

    private CustomPinEncoder newEncoder(String pepper) {
        return new CustomPinEncoder(new BCryptPasswordEncoder(4), pepper);
    }

    @Test
    void matchesTheEncodedPin() {
        assertTrue(encoder.matches(PIN, encoder.encode(PIN)));
    }

    @Test
    void rejectsAWrongPin() {
        assertFalse(encoder.matches("111111", encoder.encode(PIN)));
    }

    @Test
    void rejectsTheRightPinUnderADifferentPepper() {
        String encoded = newEncoder("a-completely-different-pepper").encode(PIN);

        assertFalse(encoder.matches(PIN, encoded));
    }

    @Test
    void neverLeaksThePinOrThePepperIntoTheHash() {
        String encoded = encoder.encode(PIN);

        assertNotEquals(PIN, encoded);
        assertFalse(encoded.contains(PIN));
        assertFalse(encoded.contains(PEPPER));
    }

    @Test
    void producesADifferentHashEachTime() {
        assertNotEquals(encoder.encode(PIN), encoder.encode(PIN));
    }

    @Test
    void rejectsNullArguments() {
        assertFalse(encoder.matches(null, encoder.encode(PIN)));
        assertFalse(encoder.matches(PIN, null));
    }

    @Test
    void refusesToStartWithoutAPepper() {
        assertThrows(IllegalArgumentException.class, () -> newEncoder("  "));
        assertThrows(IllegalArgumentException.class, () -> newEncoder(null));
    }
}
