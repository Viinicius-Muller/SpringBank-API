package vinicius.muller.SpringBank.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vinicius.muller.SpringBank.dto.AccountResponseDTO;
import vinicius.muller.SpringBank.dto.CreateAccountRequestDTO;
import vinicius.muller.SpringBank.dto.DeleteAccountRequestDTO;
import vinicius.muller.SpringBank.service.AccountService;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MEMBER')")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponseDTO> create(@Valid @RequestBody CreateAccountRequestDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(createDTO));
    }

    @GetMapping("/me")
    public ResponseEntity<AccountResponseDTO> getMyAccount() {
        return ResponseEntity.ok(accountService.getMyAccount());
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@Valid @RequestBody DeleteAccountRequestDTO deleteDTO) {
        accountService.deleteAccount(deleteDTO);
        return ResponseEntity.noContent().build();
    }
}
