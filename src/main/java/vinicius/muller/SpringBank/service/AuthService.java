package vinicius.muller.SpringBank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vinicius.muller.SpringBank.dto.AuthResponseDTO;
import vinicius.muller.SpringBank.dto.LoginRequestDTO;
import vinicius.muller.SpringBank.dto.RegisterRequestDTO;
import vinicius.muller.SpringBank.dto.UpdateCredentialsRequestDTO;
import vinicius.muller.SpringBank.exception.AlreadyRegisteredException;
import vinicius.muller.SpringBank.exception.IncorrectCredentialsException;
import vinicius.muller.SpringBank.exception.UserNotFoundByEmail;
import vinicius.muller.SpringBank.utils.SecurityUtils;
import vinicius.muller.SpringBank.infra.security.TokenService;
import vinicius.muller.SpringBank.model.User;
import vinicius.muller.SpringBank.repository.UserRepository;

// Login, Register, Update Credentials
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponseDTO registerUser(RegisterRequestDTO registerDTO) {
        if (userRepository.existsByEmail(registerDTO.email()))
            throw new AlreadyRegisteredException("E-mail already belongs to an Account");

        User user = new User();
        user.setEmail(registerDTO.email());
        user.setUsername(registerDTO.username());
        user.setPasswordHash(passwordEncoder.encode(registerDTO.password()));

        userRepository.save(user);
        return new AuthResponseDTO(tokenService.generateToken(user), user);
    }

    public AuthResponseDTO loginUser(LoginRequestDTO loginDTO) {
        User user = userRepository.findByEmail(loginDTO.email())
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found with email: " + loginDTO.email()
                        )
                );

        if (user.isPasswordCorrect(loginDTO.password(), passwordEncoder)) {
            return new AuthResponseDTO(tokenService.generateToken(user), user);
        } else throw new IncorrectCredentialsException("E-mail or password are incorrect");
    }

    @Transactional
    public AuthResponseDTO updateCredentials(UpdateCredentialsRequestDTO updateDTO) {
        User caller = authenticatedUser();

        User user = userRepository.findByEmail(caller.getEmail())
                .orElseThrow(() ->
                        new UserNotFoundByEmail(
                                "User not found with email: " + caller.getEmail()
                        )
                );

        if (!user.isPasswordCorrect(updateDTO.currentPassword(), passwordEncoder))
            throw new IncorrectCredentialsException("Current password is incorrect");

        if (updateDTO.newUsername() != null && !updateDTO.newUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(updateDTO.newUsername()))
                throw new AlreadyRegisteredException("Username already belongs to an Account");

            user.setUsername(updateDTO.newUsername());
        }

        if (updateDTO.newEmail() != null && !updateDTO.newEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(updateDTO.newEmail()))
                throw new AlreadyRegisteredException("E-mail already belongs to an Account");

            user.setEmail(updateDTO.newEmail());
        }

        if (updateDTO.newPassword() != null)
            user.setPasswordHash(passwordEncoder.encode(updateDTO.newPassword()));

        userRepository.save(user);

        // Needs to update the Token in the Frontend, if there was any update in the e-mail (subject)
        return new AuthResponseDTO(tokenService.generateToken(user), user);
    }

    private User authenticatedUser() {
        return SecurityUtils.authenticatedUser();
    }
}
