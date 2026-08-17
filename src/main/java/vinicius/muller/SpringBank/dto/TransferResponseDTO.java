package vinicius.muller.SpringBank.dto;

import vinicius.muller.SpringBank.model.Transfer;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponseDTO(Long id, Long senderAccountId, Long receiverAccountId, BigDecimal value,
                                  Instant transferDateTime) {

    public TransferResponseDTO(Transfer transfer) {
        this(transfer.getId(),
                transfer.getSenderAccount().getId(),
                transfer.getReceiverAccount().getId(),
                transfer.getValue(),
                transfer.getTransferDateTime());
    }
}
