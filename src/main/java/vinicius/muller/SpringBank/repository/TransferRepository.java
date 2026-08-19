package vinicius.muller.SpringBank.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vinicius.muller.SpringBank.model.Transfer;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    @Query("select t from Transfer t where t.senderAccount.id = :accountId or t.receiverAccount.id = :accountId")
    Page<Transfer> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);

    Page<Transfer> findBySenderAccountId(Long accountId, Pageable pageable);

    Page<Transfer> findByReceiverAccountId(Long accountId, Pageable pageable);
}
