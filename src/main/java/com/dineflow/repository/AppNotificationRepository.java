package com.dineflow.repository;

import com.dineflow.entity.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    List<AppNotification> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);

    Optional<AppNotification> findByIdAndCustomerId(Long id, Long customerId);

    @Modifying
    @Query("update AppNotification n set n.readAt = :now where n.customerId = :customerId and n.readAt is null")
    int markAllRead(@Param("customerId") Long customerId, @Param("now") Instant now);
}
