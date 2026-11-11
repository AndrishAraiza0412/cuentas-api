package com.cuentasapi.account;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Page<Account> findByUserId(Long userId, Pageable pageable);

    /** Row locks taken in ascending id order: every transaction locks in the same order, so none can deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockAllById(@Param("ids") Collection<Long> ids);

    @Query(value = "select nextval('account_number_seq')", nativeQuery = true)
    long nextNumber();
}
