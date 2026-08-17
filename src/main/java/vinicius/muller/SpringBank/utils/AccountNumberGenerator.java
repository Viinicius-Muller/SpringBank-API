package vinicius.muller.SpringBank.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

@Slf4j
@Component
public final class AccountNumberGenerator {
    public String genNumber(Long userId) {
        Random random = new Random();
        String random5digitStr = String.format("%05d", random.nextInt(99999));
        String accountNumber = userId.toString() + random5digitStr;
        log.info("Generated account number of: {}", accountNumber);

        return accountNumber; // 1-23456 (123456)
    }
}
