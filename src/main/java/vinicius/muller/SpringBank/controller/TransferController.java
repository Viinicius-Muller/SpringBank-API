package vinicius.muller.SpringBank.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vinicius.muller.SpringBank.dto.PageResponseDTO;
import vinicius.muller.SpringBank.dto.TransferRequestDTO;
import vinicius.muller.SpringBank.dto.TransferResponseDTO;
import vinicius.muller.SpringBank.service.TransferService;

@RestController
@RequestMapping("/transfers")
@Tag(name = "Transfers", description = "Transfers between accounts and paginated statements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MEMBER')")
public class TransferController {

    private final TransferService transferService;

    // sender account comes from the path, since a user may hold many accounts
    @Operation(summary = "Transfer money from the given account to another")
    @PostMapping("/{accountNumber}")
    public ResponseEntity<TransferResponseDTO> create(@Valid @RequestBody TransferRequestDTO transferDTO,
                                                      @PathVariable String accountNumber) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.createTransfer(transferDTO, accountNumber));
    }

    @Operation(summary = "Statement: transfers sent and received by the account")
    @GetMapping("/me/{accountNumber}")
    public ResponseEntity<PageResponseDTO<TransferResponseDTO>> getMyTransfers(
            @PageableDefault(size = 10, sort = "transferDateTime", direction = Sort.Direction.DESC)
            Pageable pageable,
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(transferService.getMyTransfers(pageable, accountNumber));
    }

    @Operation(summary = "Statement: transfers sent by the account")
    @GetMapping("/me/{accountNumber}/sent")
    public ResponseEntity<PageResponseDTO<TransferResponseDTO>> getMySentTransfers(
            @PageableDefault(size = 10, sort = "transferDateTime", direction = Sort.Direction.DESC)
            Pageable pageable,
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(transferService.getMySentTransfers(pageable, accountNumber));
    }

    @Operation(summary = "Statement: transfers received by the account")
    @GetMapping("/me/{accountNumber}/received")
    public ResponseEntity<PageResponseDTO<TransferResponseDTO>> getMyReceivedTransfers(
            @PageableDefault(size = 10, sort = "transferDateTime", direction = Sort.Direction.DESC)
            Pageable pageable,
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(transferService.getMyReceivedTransfers(pageable, accountNumber));
    }
}
