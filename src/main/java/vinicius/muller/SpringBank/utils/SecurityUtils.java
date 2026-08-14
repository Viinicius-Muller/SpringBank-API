package vinicius.muller.SpringBank.utils;

import org.springframework.security.core.context.SecurityContextHolder;
import vinicius.muller.SpringBank.exception.IncorrectCredentialsException;
import vinicius.muller.SpringBank.model.User;

public final class SecurityUtils {

    private SecurityUtils() {}

    // gets user authentication from SecurityContext
    public static User authenticatedUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof User user))
            throw new IncorrectCredentialsException("No authenticated caller");

        return user;
    }
}
