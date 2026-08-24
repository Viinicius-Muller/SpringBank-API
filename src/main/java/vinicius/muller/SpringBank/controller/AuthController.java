package vinicius.muller.SpringBank.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vinicius.muller.SpringBank.dto.AuthResponseDTO;
import vinicius.muller.SpringBank.dto.LoginRequestDTO;
import vinicius.muller.SpringBank.dto.RegisterRequestDTO;
import vinicius.muller.SpringBank.dto.UpdateCredentialsRequestDTO;
import vinicius.muller.SpringBank.service.AuthService;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Registration, login and credential updates")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a user and return a JWT")
    @SecurityRequirements
    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO registerDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerUser(registerDTO));
    }

    @Operation(summary = "Log in and return a JWT")
    @SecurityRequirements
    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginDTO) {
        return ResponseEntity.ok(authService.loginUser(loginDTO));
    }

    @Operation(summary = "Update the caller's username, e-mail or password")
    @PatchMapping("/credentials")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuthResponseDTO> updateCredentials(@Valid @RequestBody UpdateCredentialsRequestDTO updateDTO) {
        return ResponseEntity.ok(authService.updateCredentials(updateDTO));
    }
}
