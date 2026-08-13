package vinicius.muller.SpringBank.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vinicius.muller.SpringBank.dto.AuthResponse;
import vinicius.muller.SpringBank.dto.LoginRequest;
import vinicius.muller.SpringBank.dto.RegisterRequest;
import vinicius.muller.SpringBank.dto.UpdateCredentialsRequest;
import vinicius.muller.SpringBank.service.AuthService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerUser(registerDTO));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginDTO) {
        return ResponseEntity.ok(authService.loginUser(loginDTO));
    }

    @PatchMapping("/credentials")
    public ResponseEntity<AuthResponse> updateCredentials(@Valid @RequestBody UpdateCredentialsRequest updateDTO) {
        return ResponseEntity.ok(authService.updateCredentials(updateDTO));
    }
}
