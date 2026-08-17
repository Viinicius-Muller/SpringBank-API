package vinicius.muller.SpringBank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vinicius.muller.SpringBank.repository.TransferRepository;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TransferService {

    private final TransferRepository transferRepository;

    @Qualifier("pinEncoder") // use pinEncoder bean instead of default
    private final PasswordEncoder pinEncoder;

    /*@Transactional
    public TransferResponseDTO createTransfer(TransferRequestDTO dto, String senderAccNumber) {

    }*/
}
