package vinicius.muller.SpringBank.dto;

import vinicius.muller.SpringBank.model.Role;
import vinicius.muller.SpringBank.model.User;

public record AuthResponse(String token, String username, String email, Role role) {

    public AuthResponse (String token, User user) {
        this(token, user.getUsername(), user.getEmail(), user.getRole());
    }
}
