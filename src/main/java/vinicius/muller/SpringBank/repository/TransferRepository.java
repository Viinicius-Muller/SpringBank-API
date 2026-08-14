package vinicius.muller.SpringBank.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vinicius.muller.SpringBank.model.Transfer;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
}
