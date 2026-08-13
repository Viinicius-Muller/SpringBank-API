package vinicius.muller.SpringBank.exception;

public class InactiveLoginException extends RuntimeException {
    public InactiveLoginException(String message) {
        super(message);
    }
}
