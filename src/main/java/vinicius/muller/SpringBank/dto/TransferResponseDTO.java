package vinicius.muller.SpringBank.dto;

import vinicius.muller.SpringBank.model.Transfer;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponseDTO(Long id, Long senderAccountId, Long receiverAccountId, BigDecimal value,
                                  Instant transferDateTime, TransferDirection direction) {

    // viewerAccountId is the account the statement is being read for - it decides the direction.
    // Reading the ids off the LAZY associations does not load them, the FKs are on the transfer row
    public TransferResponseDTO(Transfer transfer, Long viewerAccountId) {
        this(transfer.getId(),
                transfer.getSenderAccount().getId(),
                transfer.getReceiverAccount().getId(),
                transfer.getValue(),
                transfer.getTransferDateTime(),
                transfer.getSenderAccount().getId().equals(viewerAccountId)
                        ? TransferDirection.SENT
                        : TransferDirection.RECEIVED);
    }
}
