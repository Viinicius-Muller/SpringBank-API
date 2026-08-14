package vinicius.muller.SpringBank.infra.security;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

// Custom Backend PinEncoder for higher protection
public class CustomPinEncoder implements PasswordEncoder {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final PasswordEncoder delegate;
    private final SecretKeySpec pepper; // create a secret key from bytes

    public CustomPinEncoder(PasswordEncoder delegate, String pepperSecret) {
        if (pepperSecret == null || pepperSecret.isBlank())
            throw new IllegalArgumentException("PIN pepper must not be blank");

        this.delegate = delegate;

        // create secret key based on var + algorithm
        this.pepper = new SecretKeySpec(pepperSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    private String hmac(CharSequence rawPin) {
        try {
            // load algorithm
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            // inject secret key
            mac.init(pepper);

            // apply into pin
            byte[] digest = mac.doFinal(rawPin.toString().getBytes(StandardCharsets.UTF_8));

            // bytes into base64 string
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to apply the PIN pepper", ex);
        }
    }

    @Override
    public @Nullable String encode(@Nullable CharSequence rawPin) {
        if (rawPin == null) return null;

        return delegate.encode(hmac(rawPin));
    }

    @Override
    public boolean matches(@Nullable CharSequence rawPin, @Nullable String encodedPin) {
        if (rawPin == null || encodedPin == null) return false;

        return delegate.matches(hmac(rawPin), encodedPin);
    }
}
