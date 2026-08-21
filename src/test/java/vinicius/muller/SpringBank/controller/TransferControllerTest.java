package vinicius.muller.SpringBank.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vinicius.muller.SpringBank.dto.PageResponseDTO;
import vinicius.muller.SpringBank.dto.TransferDirection;
import vinicius.muller.SpringBank.dto.TransferRequestDTO;
import vinicius.muller.SpringBank.dto.TransferResponseDTO;
import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.exception.UnauthorizedTransferException;
import vinicius.muller.SpringBank.infra.security.SecurityConfig;
import vinicius.muller.SpringBank.service.TransferService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransferController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
@AutoConfigureMockMvc(addFilters = false)
class TransferControllerTest {

    private static final String ACCOUNT_NUMBER = "100001";
    private static final String ALL = "/transfers/me/" + ACCOUNT_NUMBER;
    private static final String SENT = ALL + "/sent";
    private static final String RECEIVED = ALL + "/received";
    private static final String CREATE = "/transfers/" + ACCOUNT_NUMBER;

    @MockitoBean
    private TransferService transferService;

    @Autowired
    private MockMvc mockMvc;

    private final TransferResponseDTO transfer = new TransferResponseDTO(1L, 10L, 20L,
            new BigDecimal("30.00"), Instant.parse("2026-08-18T10:15:30Z"), TransferDirection.SENT);

    private final PageResponseDTO<TransferResponseDTO> page =
            new PageResponseDTO<>(List.of(transfer), 0, 10, 1, 1, true);

    private void stub(String path) {
        when(serviceCall(path, any(Pageable.class))).thenReturn(page);
    }

    private PageResponseDTO<TransferResponseDTO> serviceCall(String path, Pageable pageable) {
        return switch (path) {
            case SENT -> transferService.getMySentTransfers(pageable, eq(ACCOUNT_NUMBER));
            case RECEIVED -> transferService.getMyReceivedTransfers(pageable, eq(ACCOUNT_NUMBER));
            default -> transferService.getMyTransfers(pageable, eq(ACCOUNT_NUMBER));
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {ALL, SENT, RECEIVED})
    void returnsOkWithThePagedStatement(String path) throws Exception {
        stub(path);

        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("SENT"))
                .andExpect(jsonPath("$.content[0].value").value(30.00))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {ALL, SENT, RECEIVED})
    void defaultsToTenPerPageNewestFirst(String path) throws Exception {
        stub(path);

        mockMvc.perform(get(path)).andExpect(status().isOk());

        Pageable pageable = capturedPageable(path);
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("transferDateTime"))
                .isNotNull()
                .satisfies(order -> assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC));
    }

    @ParameterizedTest
    @ValueSource(strings = {ALL, SENT, RECEIVED})
    void honoursExplicitPageAndSize(String path) throws Exception {
        stub(path);

        mockMvc.perform(get(path).param("page", "1").param("size", "5")).andExpect(status().isOk());

        Pageable pageable = capturedPageable(path);
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {ALL, SENT, RECEIVED})
    void returnsNotFoundWhenTheAccountDoesNotExist(String path) throws Exception {
        when(serviceCall(path, any(Pageable.class)))
                .thenThrow(new AccountNotFoundException("Account not found by number: " + ACCOUNT_NUMBER));

        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {ALL, SENT, RECEIVED})
    void returnsForbiddenWhenTheAccountBelongsToSomeoneElse(String path) throws Exception {
        when(serviceCall(path, any(Pageable.class)))
                .thenThrow(new UnauthorizedTransferException("User 1 is not the owner of account 10"));

        mockMvc.perform(get(path))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied"));
    }

    @Test
    void sentAndReceivedDoNotFallThroughToTheCombinedStatement() throws Exception {
        stub(SENT);
        stub(RECEIVED);

        mockMvc.perform(get(SENT)).andExpect(status().isOk());
        mockMvc.perform(get(RECEIVED)).andExpect(status().isOk());

        verify(transferService).getMySentTransfers(any(Pageable.class), eq(ACCOUNT_NUMBER));
        verify(transferService).getMyReceivedTransfers(any(Pageable.class), eq(ACCOUNT_NUMBER));
        verify(transferService, never()).getMyTransfers(any(Pageable.class), eq(ACCOUNT_NUMBER));
    }

    @Test
    void createsTransferFromThePathAccount() throws Exception {
        when(transferService.createTransfer(any(TransferRequestDTO.class), eq(ACCOUNT_NUMBER)))
                .thenReturn(transfer);

        mockMvc.perform(post(CREATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverAccountNumber":"900002","value":30.00,"pin":"482193"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.value").value(30.00));

        ArgumentCaptor<TransferRequestDTO> captor = ArgumentCaptor.forClass(TransferRequestDTO.class);
        verify(transferService).createTransfer(captor.capture(), eq(ACCOUNT_NUMBER));
        assertThat(captor.getValue().receiverAccountNumber()).isEqualTo("900002");
    }

    @Test
    void rejectsInvalidTransferBody() throws Exception {
        mockMvc.perform(post(CREATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverAccountNumber":"nope","value":-5,"pin":"1"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"));

        verify(transferService, never()).createTransfer(any(TransferRequestDTO.class), any(String.class));
    }

    private Pageable capturedPageable(String path) {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        switch (path) {
            case SENT -> verify(transferService).getMySentTransfers(captor.capture(), eq(ACCOUNT_NUMBER));
            case RECEIVED -> verify(transferService).getMyReceivedTransfers(captor.capture(), eq(ACCOUNT_NUMBER));
            default -> verify(transferService).getMyTransfers(captor.capture(), eq(ACCOUNT_NUMBER));
        }

        return captor.getValue();
    }
}
