package vinicius.muller.SpringBank.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vinicius.muller.SpringBank.dto.AccountResponseDTO;
import vinicius.muller.SpringBank.dto.CashRequestDTO;
import vinicius.muller.SpringBank.dto.CreateAccountRequestDTO;
import vinicius.muller.SpringBank.dto.DeleteAccountRequestDTO;
import vinicius.muller.SpringBank.service.AccountService;

import java.util.List;

@RestController
@RequestMapping("/accounts")
@Tag(name = "Accounts", description = "Account opening, lookup, deposits and withdrawals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MEMBER')")
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "Open an account with a 6-digit PIN")
    @PostMapping
    public ResponseEntity<AccountResponseDTO> create(@Valid @RequestBody CreateAccountRequestDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(createDTO));
    }

    // lists the caller's accounts
    @Operation(summary = "List the caller's accounts")
    @GetMapping
    public ResponseEntity<List<AccountResponseDTO>> getMyAccounts() {
        return ResponseEntity.ok(accountService.getMyAccounts());
    }

    @Operation(summary = "Get one of the caller's accounts by number")
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponseDTO> getMyAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getMyAccount(accountNumber));
    }

    // simulated way to get money
    @Operation(summary = "Deposit into an account")
    @PostMapping("/{accountNumber}/deposit")
    public ResponseEntity<AccountResponseDTO> deposit(@Valid @RequestBody CashRequestDTO cashDTO,
                                                      @PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.deposit(cashDTO, accountNumber));
    }

    @Operation(summary = "Withdraw from an account")
    @PostMapping("/{accountNumber}/withdraw")
    public ResponseEntity<AccountResponseDTO> withdraw(@Valid @RequestBody CashRequestDTO cashDTO,
                                                       @PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.withdraw(cashDTO, accountNumber));
    }

    @Operation(summary = "Deactivate an account (soft delete)")
    @DeleteMapping("/{accountNumber}")
    public ResponseEntity<Void> delete(@Valid @RequestBody DeleteAccountRequestDTO deleteDTO,
                                       @PathVariable String accountNumber) {
        accountService.deleteAccount(deleteDTO, accountNumber);
        return ResponseEntity.noContent().build();
    }
}
