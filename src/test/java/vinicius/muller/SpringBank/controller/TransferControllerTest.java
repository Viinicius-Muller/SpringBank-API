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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vinicius.muller.SpringBank.dto.PageResponseDTO;
import vinicius.muller.SpringBank.dto.TransferDirection;
import vinicius.muller.SpringBank.dto.TransferResponseDTO;
import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.infra.security.SecurityConfig;
import vinicius.muller.SpringBank.service.TransferService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SecurityConfig is excluded for the same reason as in AccountControllerTest - addFilters = false
// means these tests prove the mapping and the paging defaults, NOT that /transfers is protected.
@WebMvcTest(controllers = TransferController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
@AutoConfigureMockMvc(addFilters = false)
class TransferControllerTest {

    private static final String ALL = "/transfers/me";
    private static final String SENT = "/transfers/me/sent";
    private static final String RECEIVED = "/transfers/me/received";

    @MockitoBean
    private TransferService transferService;

    @Autowired
    private MockMvc mockMvc;

    private final PageResponseDTO<TransferResponseDTO> page = new PageResponseDTO<>(
            List.of(new TransferResponseDTO(1L, 10L, 20L, new BigDecimal("30.00"),
                    Instant.parse("2026-08-18T10:15:30Z"), TransferDirection.SENT)),
            0, 10, 1, 1, true);

    private void stub(String path) {
        when(serviceCall(path, any(Pageable.class))).thenReturn(page);
    }

    // routes the stub/verify to the service method behind the given path
    private PageResponseDTO<TransferResponseDTO> serviceCall(String path, Pageable pageable) {
        return switch (path) {
            case SENT -> transferService.getMySentTransfers(pageable);
            case RECEIVED -> transferService.getMyReceivedTransfers(pageable);
            default -> transferService.getMyTransfers(pageable);
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
    void returnsNotFoundWhenTheCallerHasNoAccount(String path) throws Exception {
        when(serviceCall(path, any(Pageable.class)))
                .thenThrow(new AccountNotFoundException("Account not found for user: 1"));

        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }

    @Test
    void sentAndReceivedDoNotFallThroughToTheCombinedStatement() throws Exception {
        stub(SENT);
        stub(RECEIVED);

        mockMvc.perform(get(SENT)).andExpect(status().isOk());
        mockMvc.perform(get(RECEIVED)).andExpect(status().isOk());

        verify(transferService).getMySentTransfers(any(Pageable.class));
        verify(transferService).getMyReceivedTransfers(any(Pageable.class));
        verify(transferService, never()).getMyTransfers(any(Pageable.class));
    }

    private Pageable capturedPageable(String path) {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        switch (path) {
            case SENT -> verify(transferService).getMySentTransfers(captor.capture());
            case RECEIVED -> verify(transferService).getMyReceivedTransfers(captor.capture());
            default -> verify(transferService).getMyTransfers(captor.capture());
        }

        return captor.getValue();
    }
}
