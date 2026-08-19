package vinicius.muller.SpringBank.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vinicius.muller.SpringBank.dto.AccountResponseDTO;
import vinicius.muller.SpringBank.dto.CashRequestDTO;
import vinicius.muller.SpringBank.dto.CreateAccountRequestDTO;
import vinicius.muller.SpringBank.dto.DeleteAccountRequestDTO;
import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.exception.AccountNumberGenerationException;
import vinicius.muller.SpringBank.exception.UnauthorizedTransferException;
import vinicius.muller.SpringBank.exception.IncorrectCredentialsException;
import vinicius.muller.SpringBank.exception.InsufficientBalanceException;
import vinicius.muller.SpringBank.exception.InvalidAccountCredentialsException;
import vinicius.muller.SpringBank.infra.security.SecurityConfig;
import vinicius.muller.SpringBank.service.AccountService;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SecurityConfig is excluded for the same reason as in AuthControllerTest - @WebMvcTest would
// otherwise build the real filter chain. addFilters = false means these tests prove the mapping
// and the status mapping, NOT that /accounts is protected.
@WebMvcTest(controllers = AccountController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    private static final String EMAIL = "vinicius@springbank.dev";
    private static final String USERNAME = "vinicius";
    private static final String PIN = "482193";
    private static final String ACCOUNT_NUMBER = "100001";
    private static final String ACCOUNT_PATH = "/accounts/" + ACCOUNT_NUMBER;

    @MockitoBean
    private AccountService accountService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final AccountResponseDTO response =
            new AccountResponseDTO(10L, ACCOUNT_NUMBER, USERNAME, EMAIL, BigDecimal.ZERO, true);

    @Test
    void createReturnsCreatedWithAccount() throws Exception {
        when(accountService.createAccount(any(CreateAccountRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateAccountRequestDTO(PIN))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createRejectsMalformedPin() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateAccountRequestDTO("12a"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pin").exists());

        verify(accountService, never()).createAccount(any(CreateAccountRequestDTO.class));
    }

    @Test
    void createReturnsServiceUnavailableWhenNoAccountNumberIsFree() throws Exception {
        when(accountService.createAccount(any(CreateAccountRequestDTO.class)))
                .thenThrow(new AccountNumberGenerationException("no free account number"));

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateAccountRequestDTO(PIN))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void listReturnsEveryAccountOfTheCaller() throws Exception {
        var second = new AccountResponseDTO(11L, "200002", USERNAME, EMAIL, BigDecimal.ZERO, true);
        when(accountService.getMyAccounts()).thenReturn(List.of(response, second));

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$[1].accountNumber").value("200002"));
    }

    @Test
    void getMyAccountReturnsOk() throws Exception {
        when(accountService.getMyAccount(ACCOUNT_NUMBER)).thenReturn(response);

        mockMvc.perform(get(ACCOUNT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void getMyAccountReturnsNotFoundWhenMissing() throws Exception {
        when(accountService.getMyAccount(ACCOUNT_NUMBER))
                .thenThrow(new AccountNotFoundException("Account not found by number: " + ACCOUNT_NUMBER));

        mockMvc.perform(get(ACCOUNT_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }

    @Test
    void getMyAccountReturnsForbiddenWhenOwnedBySomeoneElse() throws Exception {
        when(accountService.getMyAccount(ACCOUNT_NUMBER))
                .thenThrow(new UnauthorizedTransferException("User 1 is not the owner of account 10"));

        mockMvc.perform(get(ACCOUNT_PATH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        var request = new DeleteAccountRequestDTO(PIN);

        mockMvc.perform(delete(ACCOUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isNoContent());

        verify(accountService).deleteAccount(request, ACCOUNT_NUMBER);
    }

    @Test
    void deleteReturnsUnauthorizedOnWrongPin() throws Exception {
        doThrow(new IncorrectCredentialsException("PIN is incorrect"))
                .when(accountService).deleteAccount(any(DeleteAccountRequestDTO.class), eq(ACCOUNT_NUMBER));

        mockMvc.perform(delete(ACCOUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DeleteAccountRequestDTO("000000"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void deleteRejectsBlankPin() throws Exception {
        mockMvc.perform(delete(ACCOUNT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DeleteAccountRequestDTO("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pin").exists());

        verify(accountService, never()).deleteAccount(any(DeleteAccountRequestDTO.class), any(String.class));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // --- deposit / withdraw ---

    private static final AccountResponseDTO FUNDED =
            new AccountResponseDTO(10L, ACCOUNT_NUMBER, USERNAME, EMAIL, new BigDecimal("100.00"), true);

    @Test
    void depositReturnsOkWithTheNewBalance() throws Exception {
        when(accountService.deposit(any(CashRequestDTO.class), eq(ACCOUNT_NUMBER))).thenReturn(FUNDED);

        mockMvc.perform(post(ACCOUNT_PATH + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CashRequestDTO(new BigDecimal("100.00"), PIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void withdrawReturnsOkWithTheNewBalance() throws Exception {
        when(accountService.withdraw(any(CashRequestDTO.class), eq(ACCOUNT_NUMBER))).thenReturn(FUNDED);

        mockMvc.perform(post(ACCOUNT_PATH + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CashRequestDTO(new BigDecimal("40.00"), PIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void depositRejectsNonPositiveValue() throws Exception {
        mockMvc.perform(post(ACCOUNT_PATH + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CashRequestDTO(new BigDecimal("0.00"), PIN))))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).deposit(any(CashRequestDTO.class), any());
    }

    @Test
    void depositRejectsMissingValue() throws Exception {
        mockMvc.perform(post(ACCOUNT_PATH + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CashRequestDTO(null, PIN))))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).deposit(any(CashRequestDTO.class), any());
    }

    @Test
    void depositRejectsMalformedPin() throws Exception {
        mockMvc.perform(post(ACCOUNT_PATH + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CashRequestDTO(new BigDecimal("10.00"), "abc"))))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).deposit(any(CashRequestDTO.class), any());
    }

    @Test
    void depositReturnsUnauthorizedOnWrongPin() throws Exception {
        when(accountService.deposit(any(CashRequestDTO.class), eq(ACCOUNT_NUMBER)))
                .thenThrow(new InvalidAccountCredentialsException("bad pin"));

        mockMvc.perform(post(ACCOUNT_PATH + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CashRequestDTO(new BigDecimal("10.00"), PIN))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawReturnsUnprocessableWhenBalanceIsTooLow() throws Exception {
        when(accountService.withdraw(any(CashRequestDTO.class), eq(ACCOUNT_NUMBER)))
                .thenThrow(new InsufficientBalanceException("not enough"));

        mockMvc.perform(post(ACCOUNT_PATH + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CashRequestDTO(new BigDecimal("999.00"), PIN))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void depositReturnsNotFoundForUnknownAccount() throws Exception {
        when(accountService.deposit(any(CashRequestDTO.class), eq("999999")))
                .thenThrow(new AccountNotFoundException("nope"));

        mockMvc.perform(post("/accounts/999999/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CashRequestDTO(new BigDecimal("10.00"), PIN))))
                .andExpect(status().isNotFound());
    }

    @Test
    void depositReturnsForbiddenForAnotherUsersAccount() throws Exception {
        when(accountService.deposit(any(CashRequestDTO.class), eq(ACCOUNT_NUMBER)))
                .thenThrow(new UnauthorizedTransferException("not owner"));

        mockMvc.perform(post(ACCOUNT_PATH + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CashRequestDTO(new BigDecimal("10.00"), PIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedBodyIsBadRequestNotServerError() throws Exception {
        mockMvc.perform(post(ACCOUNT_PATH + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());
    }
}
