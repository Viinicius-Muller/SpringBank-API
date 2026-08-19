package vinicius.muller.SpringBank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vinicius.muller.SpringBank.dto.PageResponseDTO;
import vinicius.muller.SpringBank.dto.TransferRequestDTO;
import vinicius.muller.SpringBank.dto.TransferResponseDTO;
import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.exception.InactiveAccountException;
import vinicius.muller.SpringBank.exception.InsufficientBalanceException;
import vinicius.muller.SpringBank.exception.InvalidAccountCredentialsException;
import vinicius.muller.SpringBank.exception.SelfTransferException;
import vinicius.muller.SpringBank.exception.UnauthorizedTransferException;
import vinicius.muller.SpringBank.model.Account;
import vinicius.muller.SpringBank.model.Transfer;
import vinicius.muller.SpringBank.model.User;
import vinicius.muller.SpringBank.repository.AccountRepository;
import vinicius.muller.SpringBank.repository.TransferRepository;
import vinicius.muller.SpringBank.utils.AccountUtils;
import vinicius.muller.SpringBank.utils.SecurityUtils;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;

    @Qualifier("pinEncoder") // use pinEncoder bean instead of default
    private final PasswordEncoder pinEncoder;

    @Transactional
    public TransferResponseDTO createTransfer(TransferRequestDTO dto, String senderAccNumber) {
        User caller = SecurityUtils.authenticatedUser();
        String receiverAccNumber = dto.receiverAccountNumber();

        if (receiverAccNumber.equals(senderAccNumber))
            throw new SelfTransferException("Cannot transfer to the same account");

        // lock in account-number order so opposite-direction transfers cannot deadlock
        boolean senderFirst = senderAccNumber.compareTo(receiverAccNumber) < 0;
        Account firstLocked = lockedAccount(senderFirst ? senderAccNumber : receiverAccNumber);
        Account secondLocked = lockedAccount(senderFirst ? receiverAccNumber : senderAccNumber);

        Account senderAccount = senderFirst ? firstLocked : secondLocked;
        Account receiverAccount = senderFirst ? secondLocked : firstLocked;

        this.validateTransaction(caller, dto, senderAccount, receiverAccount);

        senderAccount.setBalance(senderAccount.getBalance().subtract(dto.value()));
        receiverAccount.setBalance(receiverAccount.getBalance().add(dto.value()));

        Transfer transfer = new Transfer();
        transfer.setSenderAccount(senderAccount);
        transfer.setReceiverAccount(receiverAccount);
        transfer.setValue(dto.value());

        // balances are flushed on transfer commit
        transferRepository.save(transfer);

        log.info("Transfer {} of {} from account {} to account {}",
                transfer.getId(), transfer.getValue(), senderAccount.getId(), receiverAccount.getId());

        return new TransferResponseDTO(transfer, senderAccount.getId());
    }

    public PageResponseDTO<TransferResponseDTO> getMyTransfers(Pageable pageable) {
        Account account = AccountUtils.callerAccount(accountRepository);

        return new PageResponseDTO<>(transferRepository.findByAccountId(account.getId(), pageable)
                .map(transfer -> new TransferResponseDTO(transfer, account.getId())));
    }

    public PageResponseDTO<TransferResponseDTO> getMySentTransfers(Pageable pageable) {
        Account account = AccountUtils.callerAccount(accountRepository);

        return new PageResponseDTO<>(transferRepository.findBySenderAccountId(account.getId(), pageable)
                .map(transfer -> new TransferResponseDTO(transfer, account.getId())));
    }

    public PageResponseDTO<TransferResponseDTO> getMyReceivedTransfers(Pageable pageable) {
        Account account = AccountUtils.callerAccount(accountRepository);

        return new PageResponseDTO<>(transferRepository.findByReceiverAccountId(account.getId(), pageable)
                .map(transfer -> new TransferResponseDTO(transfer, account.getId())));
    }

    public void validateTransaction(User caller, TransferRequestDTO dto, Account senderAccount, Account receiverAccount) {
        if (!senderAccount.getUser().getId().equals(caller.getId()))
            throw new UnauthorizedTransferException(
                    "User " + caller.getId() + " is not the owner of account " + senderAccount.getId());

        if (!senderAccount.isPinCorrect(dto.pin(), pinEncoder))
            throw new InvalidAccountCredentialsException("Failed to validate Account credentials");

        // Soft-delete validation
        if (!senderAccount.getActive() || !receiverAccount.getActive())
            throw new InactiveAccountException("Transfer involves an inactive account");

        if (senderAccount.getBalance().compareTo(dto.value()) < 0)
            throw new InsufficientBalanceException("Not enough balance to transfer");
    }

    private Account lockedAccount(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
    }
}
