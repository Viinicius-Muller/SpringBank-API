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
import vinicius.muller.SpringBank.dto.AuthResponseDTO;
import vinicius.muller.SpringBank.dto.LoginRequestDTO;
import vinicius.muller.SpringBank.dto.RegisterRequestDTO;
import vinicius.muller.SpringBank.dto.UpdateCredentialsRequestDTO;
import vinicius.muller.SpringBank.exception.AlreadyRegisteredException;
import vinicius.muller.SpringBank.exception.IncorrectCredentialsException;
import vinicius.muller.SpringBank.infra.security.SecurityConfig;
import vinicius.muller.SpringBank.model.Role;
import vinicius.muller.SpringBank.service.AuthService;

import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SecurityConfig is excluded because @WebMvcTest would otherwise try to build the real
// filter chain, which needs TokenService, UserRepository and the two REST handlers.
//
// addFilters = false means these tests prove the mapping exists at a given path, NOT that
// PATCH /auth/credentials is protected - the filter chain rule is covered by SecurityFilterTest
// and by the anonymous/no-auth cases in AuthServiceTest.
@WebMvcTest(controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String TOKEN = "a.jwt.token";
    private static final String EMAIL = "vinicius@springbank.dev";
    private static final String USERNAME = "vinicius";
    private static final String PASSWORD = "sup3r-secret";

    @MockitoBean
    private AuthService authService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final AuthResponseDTO response = new AuthResponseDTO(TOKEN, USERNAME, EMAIL, Role.MEMBER);

    @Test
    void registerReturnsCreatedWithToken() throws Exception {
        when(authService.registerUser(any(RegisterRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequestDTO(USERNAME, EMAIL, PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(TOKEN))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void registerRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequestDTO("  ", "not-an-email", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void registerReturnsConflictOnDuplicate() throws Exception {
        when(authService.registerUser(any(RegisterRequestDTO.class)))
                .thenThrow(new AlreadyRegisteredException("E-mail already belongs to an Account"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequestDTO(USERNAME, EMAIL, PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("E-mail already belongs to an Account"));
    }

    @Test
    void loginReturnsOkWithToken() throws Exception {
        when(authService.loginUser(any(LoginRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequestDTO(EMAIL, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(TOKEN));
    }

    // The vague wording matters - it must not leak whether the e-mail exists
    @Test
    void loginReturnsUnauthorizedOnBadCredentials() throws Exception {
        when(authService.loginUser(any(LoginRequestDTO.class)))
                .thenThrow(new IncorrectCredentialsException("E-mail or password are incorrect"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequestDTO(EMAIL, PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void updateCredentialsReturnsOkWithFreshToken() throws Exception {
        when(authService.updateCredentials(any(UpdateCredentialsRequestDTO.class))).thenReturn(response);
        var request = new UpdateCredentialsRequestDTO(PASSWORD, null, null, "n3w-password");

        mockMvc.perform(patch("/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(TOKEN));

        verify(authService).updateCredentials(request);
    }

    @Test
    void updateCredentialsRejectsRequestWithNothingToChange() throws Exception {
        var request = new UpdateCredentialsRequestDTO(PASSWORD, null, null, null);

        mockMvc.perform(patch("/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.anyChangeRequested").exists());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
