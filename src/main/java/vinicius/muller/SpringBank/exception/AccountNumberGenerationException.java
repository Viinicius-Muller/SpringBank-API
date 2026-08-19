package vinicius.muller.SpringBank.exception;

public class AccountNumberGenerationException extends RuntimeException {
    public AccountNumberGenerationException(String message) {
        super(message);
    }
}
