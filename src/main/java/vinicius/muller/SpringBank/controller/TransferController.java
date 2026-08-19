package vinicius.muller.SpringBank.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vinicius.muller.SpringBank.dto.PageResponseDTO;
import vinicius.muller.SpringBank.dto.TransferResponseDTO;
import vinicius.muller.SpringBank.service.TransferService;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MEMBER')")
public class TransferController {

    private final TransferService transferService;

    @GetMapping("/me")
    public ResponseEntity<PageResponseDTO<TransferResponseDTO>> getMyTransfers(
            @PageableDefault(size = 10, sort = "transferDateTime", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(transferService.getMyTransfers(pageable));
    }

    @GetMapping("/me/sent")
    public ResponseEntity<PageResponseDTO<TransferResponseDTO>> getMySentTransfers(
            @PageableDefault(size = 10, sort = "transferDateTime", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(transferService.getMySentTransfers(pageable));
    }

    @GetMapping("/me/received")
    public ResponseEntity<PageResponseDTO<TransferResponseDTO>> getMyReceivedTransfers(
            @PageableDefault(size = 10, sort = "transferDateTime", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(transferService.getMyReceivedTransfers(pageable));
    }
}
