package vinicius.muller.SpringBank.infra.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vinicius.muller.SpringBank.repository.UserRepository;
import vinicius.muller.SpringBank.model.User;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(@NonNull String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found with email: " + email
                        )
                );
        log.info("Loading User authentication {}",user.getEmail());

        // The entity implements UserDetails, so returning it keeps the role authorities
        // and the enabled flag that the provider's pre-auth checks rely on
        return user;
    }
}
