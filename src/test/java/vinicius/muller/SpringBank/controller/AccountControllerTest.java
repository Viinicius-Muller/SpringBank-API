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
import vinicius.muller.SpringBank.dto.AccountResponse;
import vinicius.muller.SpringBank.dto.CreateAccountRequest;
import vinicius.muller.SpringBank.dto.DeleteAccountRequest;
import vinicius.muller.SpringBank.exception.AccountNotFoundException;
import vinicius.muller.SpringBank.exception.AlreadyRegisteredException;
import vinicius.muller.SpringBank.exception.IncorrectCredentialsException;
import vinicius.muller.SpringBank.infra.security.SecurityConfig;
import vinicius.muller.SpringBank.service.AccountService;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
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
    private static final String PIN = "4821";

    @MockitoBean
    private AccountService accountService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final AccountResponse response =
            new AccountResponse(10L, USERNAME, EMAIL, BigDecimal.ZERO, true);

    @Test
    void createReturnsCreatedWithAccount() throws Exception {
        when(accountService.createAccount(any(CreateAccountRequest.class))).thenReturn(response);

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateAccountRequest(PIN))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createRejectsMalformedPin() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateAccountRequest("12a"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pin").exists());

        verify(accountService, never()).createAccount(any(CreateAccountRequest.class));
    }

    @Test
    void createReturnsConflictWhenAccountExists() throws Exception {
        when(accountService.createAccount(any(CreateAccountRequest.class)))
                .thenThrow(new AlreadyRegisteredException("User already has an Account"));

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateAccountRequest(PIN))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User already has an Account"));
    }

    @Test
    void getMyAccountReturnsOk() throws Exception {
        when(accountService.getMyAccount()).thenReturn(response);

        mockMvc.perform(get("/accounts/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void getMyAccountReturnsNotFoundWhenMissing() throws Exception {
        when(accountService.getMyAccount())
                .thenThrow(new AccountNotFoundException("Account not found for user: 1"));

        mockMvc.perform(get("/accounts/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        var request = new DeleteAccountRequest(PIN);

        mockMvc.perform(delete("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isNoContent());

        verify(accountService).deleteAccount(request);
    }

    @Test
    void deleteReturnsUnauthorizedOnWrongPin() throws Exception {
        doThrow(new IncorrectCredentialsException("PIN is incorrect"))
                .when(accountService).deleteAccount(any(DeleteAccountRequest.class));

        mockMvc.perform(delete("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DeleteAccountRequest("0000"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void deleteRejectsBlankPin() throws Exception {
        mockMvc.perform(delete("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DeleteAccountRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pin").exists());

        verify(accountService, never()).deleteAccount(any(DeleteAccountRequest.class));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
