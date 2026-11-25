package com.dineflow.repository;

import com.dineflow.entity.Shift;
import com.dineflow.enums.ShiftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    Optional<Shift> findFirstByCashier_IdAndStatus(Long cashierId, ShiftStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shift s where s.cashier.id = :cashierId and s.status = :status")
    List<Shift> lockOpenOf(@Param("cashierId") Long cashierId, @Param("status") ShiftStatus status);

    @Query("select s from Shift s where s.openedAt >= :from and s.openedAt < :to order by s.openedAt desc")
    List<Shift> findOpenedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
