package vinicius.muller.SpringBank.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Slf4j
@Component
public final class AccountNumberGenerator {

    // better for cryptography
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int UPPER_BOUND = 1_000_000;

    public String genNumber() {
        String accountNumber = String.format("%06d", RANDOM.nextInt(UPPER_BOUND));
        log.info("Generated account number of: {}", accountNumber);

        return accountNumber;
    }
}
