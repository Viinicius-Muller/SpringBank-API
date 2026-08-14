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
import vinicius.muller.SpringBank.dto.AccountResponse;
import vinicius.muller.SpringBank.dto.CreateAccountRequest;
import vinicius.muller.SpringBank.dto.DeleteAccountRequest;
import vinicius.muller.SpringBank.service.AccountService;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MEMBER')")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(createDTO));
    }

    @GetMapping("/me")
    public ResponseEntity<AccountResponse> getMyAccount() {
        return ResponseEntity.ok(accountService.getMyAccount());
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@Valid @RequestBody DeleteAccountRequest deleteDTO) {
        accountService.deleteAccount(deleteDTO);
        return ResponseEntity.noContent().build();
    }
}
