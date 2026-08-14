package vinicius.muller.SpringBank.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vinicius.muller.SpringBank.model.Account;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<Account> findByUserId(Long userId);

    Boolean existsByUserId(Long userId);
}
