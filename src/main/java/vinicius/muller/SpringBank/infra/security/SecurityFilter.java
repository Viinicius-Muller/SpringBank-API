package vinicius.muller.SpringBank.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import vinicius.muller.SpringBank.repository.UserRepository;
import vinicius.muller.SpringBank.model.User;

import java.io.IOException;
import java.util.Optional;

@Slf4j
public class SecurityFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final UserRepository userRepository;

    public SecurityFilter(TokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override // Runs once per request; authenticates the caller when a valid token is present
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        authenticate(request);
        filterChain.doFilter(request, response);
    }

    // No token = return null | Valid token = create authentication state
    private void authenticate(HttpServletRequest request) {
        String token = recoverToken(request);

        if (token == null || !tokenService.isTokenValid(token)) return;

        String emailSubject = tokenService.extractSubject(token);
        if (emailSubject == null) return;

        Optional<User> found = userRepository.findByEmail(emailSubject);
        if (found.isEmpty()) {
            log.warn("Token subject has no matching user");
            return;
        }

        User user = found.get();
        if (!user.isEnabled()) {
            log.warn("Rejected token for a disabled account");
            return;
        }

        log.debug("Authenticated request for {}", emailSubject);

        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    // Reads the bearer token out of the Authorization header, or null when there isn't one
    private String recoverToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);

        if (header == null || !header.startsWith(PREFIX)) return null;

        String token = header.replace(PREFIX, "").trim();

        return token.isEmpty() ? null : token;
    }

}
