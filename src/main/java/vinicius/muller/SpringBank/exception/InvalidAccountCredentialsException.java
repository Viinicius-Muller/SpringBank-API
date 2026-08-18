package vinicius.muller.SpringBank.exception;

public class InvalidAccountCredentialsException extends RuntimeException {
    public InvalidAccountCredentialsException(String message) {
        super(message);
    }
}
