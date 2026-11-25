package com.dineflow.repository;

import com.dineflow.entity.Invoice;
import com.dineflow.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByOrder_Id(Long orderId);

    @Query("select i from Invoice i where i.status = :status and i.paidAt >= :from and i.paidAt < :to order by i.paidAt")
    List<Invoice> findPaidBetween(@Param("status") InvoiceStatus status, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select i.paidAt from Invoice i where i.status = :status and i.paidAt is not null")
    List<Instant> findPaidMoments(@Param("status") InvoiceStatus status);

    @Query(value = "select i from Invoice i where i.status = :status"
            + " and (:from is null or i.paidAt >= :from) and (:to is null or i.paidAt < :to)"
            + " and (:cashierId is null or i.cashier.id = :cashierId) order by i.paidAt desc, i.id desc",
            countQuery = "select count(i) from Invoice i where i.status = :status"
            + " and (:from is null or i.paidAt >= :from) and (:to is null or i.paidAt < :to)"
            + " and (:cashierId is null or i.cashier.id = :cashierId)")
    Page<Invoice> search(@Param("status") InvoiceStatus status, @Param("from") Instant from, @Param("to") Instant to,
                         @Param("cashierId") Long cashierId, Pageable pageable);

    List<Invoice> findByShift_Id(Long shiftId);

    long countByShift_Id(Long shiftId);
}
