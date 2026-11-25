package com.dineflow.repository;

import com.dineflow.entity.Order;
import com.dineflow.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    boolean existsByCode(String code);
    Optional<Order> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") Long id);

    @Query("select o from Order o where o.status = :status and o.cancelledAt >= :from and o.cancelledAt < :to order by o.cancelledAt desc")
    List<Order> findCancelledBetween(@Param("status") OrderStatus status, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(o) from Order o where o.createdAt >= :from and o.status <> :cancelled")
    long countCreatedSince(@Param("from") Instant from, @Param("cancelled") OrderStatus cancelled);

    @Query("select count(distinct o.customer.id) from Order o where o.createdAt >= :from and o.status <> :cancelled and o.customer is not null")
    long countDistinctCustomersSince(@Param("from") Instant from, @Param("cancelled") OrderStatus cancelled);
}
