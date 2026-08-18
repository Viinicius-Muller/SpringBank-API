package vinicius.muller.SpringBank.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vinicius.muller.SpringBank.dto.TransferRequestDTO;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferServiceTest {

    private static final String SENDER_NUMBER = "100001";
    private static final String RECEIVER_NUMBER = "900002";
    private static final String PIN = "4821";

    private AccountRepository accountRepository;
    private TransferRepository transferRepository;
    private TransferService transferService;
    private User sender;
    private Account senderAccount;
    private Account receiverAccount;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        transferRepository = mock(TransferRepository.class);
        PasswordEncoder pinEncoder = new BCryptPasswordEncoder();
        transferService = new TransferService(transferRepository, accountRepository, pinEncoder);

        sender = new User();
        sender.setId(1L);
        sender.setEmail("vinicius@springbank.dev");

        User receiver = new User();
        receiver.setId(2L);
        receiver.setEmail("other@springbank.dev");

        senderAccount = new Account();
        senderAccount.setId(10L);
        senderAccount.setUser(sender);
        senderAccount.setAccountNumber(SENDER_NUMBER);
        senderAccount.setPinHash(pinEncoder.encode(PIN));
        senderAccount.setBalance(new BigDecimal("100.00"));

        receiverAccount = new Account();
        receiverAccount.setId(20L);
        receiverAccount.setUser(receiver);
        receiverAccount.setAccountNumber(RECEIVER_NUMBER);
        receiverAccount.setPinHash(pinEncoder.encode("0000"));
        receiverAccount.setBalance(new BigDecimal("5.00"));

        when(accountRepository.findByAccountNumberForUpdate(SENDER_NUMBER))
                .thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByAccountNumberForUpdate(RECEIVER_NUMBER))
                .thenReturn(Optional.of(receiverAccount));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(call -> call.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(sender, null, sender.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private TransferRequestDTO request(String receiverNumber, String value, String pin) {
        return new TransferRequestDTO(receiverNumber, new BigDecimal(value), pin);
    }

    @Test
    void movesMoneyBetweenBothAccounts() {
        var response = transferService.createTransfer(request(RECEIVER_NUMBER, "30.00", PIN), SENDER_NUMBER);

        assertThat(senderAccount.getBalance()).isEqualByComparingTo("70.00");
        assertThat(receiverAccount.getBalance()).isEqualByComparingTo("35.00");

        assertThat(response.senderAccountId()).isEqualTo(10L);
        assertThat(response.receiverAccountId()).isEqualTo(20L);
        assertThat(response.value()).isEqualByComparingTo("30.00");
    }

    @Test
    void locksBothRowsInAccountNumberOrder() {
        transferService.createTransfer(request(RECEIVER_NUMBER, "30.00", PIN), SENDER_NUMBER);

        var inOrder = org.mockito.Mockito.inOrder(accountRepository);
        inOrder.verify(accountRepository).findByAccountNumberForUpdate(SENDER_NUMBER);
        inOrder.verify(accountRepository).findByAccountNumberForUpdate(RECEIVER_NUMBER);
    }

    @Test
    void locksLowerAccountNumberFirstEvenWhenItIsTheReceiver() {
        // sender "900002" -> receiver "100001": the receiver must be locked first
        senderAccount.setAccountNumber(RECEIVER_NUMBER);
        receiverAccount.setAccountNumber(SENDER_NUMBER);
        when(accountRepository.findByAccountNumberForUpdate(RECEIVER_NUMBER))
                .thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByAccountNumberForUpdate(SENDER_NUMBER))
                .thenReturn(Optional.of(receiverAccount));

        transferService.createTransfer(request(SENDER_NUMBER, "30.00", PIN), RECEIVER_NUMBER);

        var inOrder = org.mockito.Mockito.inOrder(accountRepository);
        inOrder.verify(accountRepository).findByAccountNumberForUpdate(SENDER_NUMBER);
        inOrder.verify(accountRepository).findByAccountNumberForUpdate(RECEIVER_NUMBER);
    }

    @Test
    void allowsTransferOfTheEntireBalance() {
        transferService.createTransfer(request(RECEIVER_NUMBER, "100.00", PIN), SENDER_NUMBER);

        assertThat(senderAccount.getBalance()).isEqualByComparingTo("0.00");
        assertThat(receiverAccount.getBalance()).isEqualByComparingTo("105.00");
    }

    @Test
    void rejectsTransferAboveBalance() {
        assertThatThrownBy(() -> transferService.createTransfer(
                request(RECEIVER_NUMBER, "100.01", PIN), SENDER_NUMBER))
                .isInstanceOf(InsufficientBalanceException.class);

        assertNothingMoved();
    }

    @Test
    void rejectsWrongPin() {
        assertThatThrownBy(() -> transferService.createTransfer(
                request(RECEIVER_NUMBER, "30.00", "9999"), SENDER_NUMBER))
                .isInstanceOf(InvalidAccountCredentialsException.class);

        assertNothingMoved();
    }

    @Test
    void rejectsCallerWhoIsNotTheSenderOwner() {
        User intruder = new User();
        intruder.setId(99L);
        intruder.setEmail("intruder@springbank.dev");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(intruder, null, intruder.getAuthorities()));

        assertThatThrownBy(() -> transferService.createTransfer(
                request(RECEIVER_NUMBER, "30.00", PIN), SENDER_NUMBER))
                .isInstanceOf(UnauthorizedTransferException.class);

        assertNothingMoved();
    }

    @Test
    void rejectsSelfTransferWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> transferService.createTransfer(
                request(SENDER_NUMBER, "30.00", PIN), SENDER_NUMBER))
                .isInstanceOf(SelfTransferException.class);

        verify(accountRepository, never()).findByAccountNumberForUpdate(any());
        assertNothingMoved();
    }

    @Test
    void rejectsUnknownReceiver() {
        when(accountRepository.findByAccountNumberForUpdate("999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.createTransfer(
                request("999999", "30.00", PIN), SENDER_NUMBER))
                .isInstanceOf(AccountNotFoundException.class);

        assertNothingMoved();
    }

    @Test
    void rejectsInactiveSender() {
        senderAccount.setActive(false);

        assertThatThrownBy(() -> transferService.createTransfer(
                request(RECEIVER_NUMBER, "30.00", PIN), SENDER_NUMBER))
                .isInstanceOf(InactiveAccountException.class);

        assertNothingMoved();
    }

    @Test
    void rejectsInactiveReceiver() {
        receiverAccount.setActive(false);

        assertThatThrownBy(() -> transferService.createTransfer(
                request(RECEIVER_NUMBER, "30.00", PIN), SENDER_NUMBER))
                .isInstanceOf(InactiveAccountException.class);

        assertNothingMoved();
    }

    private void assertNothingMoved() {
        assertThat(senderAccount.getBalance()).isEqualByComparingTo("100.00");
        assertThat(receiverAccount.getBalance()).isEqualByComparingTo("5.00");
        verify(transferRepository, never()).save(any(Transfer.class));
    }
}
