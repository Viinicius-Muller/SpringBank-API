package vinicius.muller.SpringBank;

import org.springframework.data.jpa.repository.JpaRepository;
import vinicius.muller.SpringBank.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
