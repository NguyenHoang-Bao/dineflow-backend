package com.dineflow.repository;

import com.dineflow.entity.Reservation;
import com.dineflow.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    boolean existsByCode(String code);
    Optional<Reservation> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> lockById(@Param("id") Long id);

    /** Lượt giữ bàn đã quá hạn mà khách chưa xác nhận. */
    @Query("select r from Reservation r where r.status = :upcoming and r.holdExpiresAt is not null and r.holdExpiresAt <= :now")
    List<Reservation> findLapsedHolds(@Param("upcoming") ReservationStatus upcoming, @Param("now") Instant now);

    /** Lượt đã xác nhận mà khách trễ quá GRACE_MINUTES. */
    @Query("select r from Reservation r where r.status = :upcoming and r.holdExpiresAt is null and r.dateTime < :before")
    List<Reservation> findNoShows(@Param("upcoming") ReservationStatus upcoming, @Param("before") Instant before);

    @Modifying
    @Query("update Reservation r set r.status = :cancelled, r.updatedAt = CURRENT_TIMESTAMP where r.id = :id and r.status = :upcoming")
    int cancelIfUpcoming(@Param("id") Long id, @Param("upcoming") ReservationStatus upcoming, @Param("cancelled") ReservationStatus cancelled);

    /** Lượt đặt đang chiếm bàn (sắp tới và đã xác nhận hoặc còn hạn giữ), giờ hẹn trong [from, to]. */
    @Query("select r from Reservation r left join fetch r.customer where r.status = :upcoming"
            + " and (r.holdExpiresAt is null or r.holdExpiresAt > :now)"
            + " and r.dateTime between :from and :to order by r.dateTime")
    List<Reservation> findBlocking(@Param("upcoming") ReservationStatus upcoming, @Param("now") Instant now,
                                   @Param("from") Instant from, @Param("to") Instant to);

    List<Reservation> findByStatusAndPreOrderItemsIsNotNull(ReservationStatus status);

    @Query("select r from Reservation r left join fetch r.customer where r.dateTime >= :from and r.dateTime < :to"
            + " and r.holdExpiresAt is null and (:status is null or r.status = :status) order by r.dateTime")
    List<Reservation> findForDay(@Param("from") Instant from, @Param("to") Instant to, @Param("status") ReservationStatus status);

    @Query("select r from Reservation r where r.customer.id = :customerId and (:status is null or r.status = :status) order by r.dateTime desc")
    List<Reservation> findMine(@Param("customerId") Long customerId, @Param("status") ReservationStatus status);

    /** Các đơn trong ngày chưa gửi push nhắc nhở lúc 12h */
    @Query("select r from Reservation r left join fetch r.customer where r.status = :status"
            + " and r.holdExpiresAt is null and r.dateTime >= :from and r.dateTime < :to"
            + " and r.dateTime > :now and r.remindedNoonAt is null")
    List<Reservation> findUpcomingForNoonReminder(@Param("status") ReservationStatus status,
                                                  @Param("from") Instant from,
                                                  @Param("to") Instant to,
                                                  @Param("now") Instant now);

    /** Các đơn sắp tới giờ hẹn trong khoảng [now, maxTime] chưa gửi nhắc trước 2 tiếng */
    @Query("select r from Reservation r left join fetch r.customer where r.status = :status"
            + " and r.holdExpiresAt is null and r.dateTime >= :now and r.dateTime <= :maxTime"
            + " and r.reminded2hAt is null")
    List<Reservation> findUpcomingFor2hReminder(@Param("status") ReservationStatus status,
                                                @Param("now") Instant now,
                                                @Param("maxTime") Instant maxTime);

    /** Các lượt đặt đã bị hủy mà khách đã đóng cọc -> tiền cọc giữ lại tính vào doanh thu nhà hàng. */
    @Query("select r from Reservation r where r.status = :cancelled and r.depositPaidAt is not null"
            + " and r.depositAmount is not null and r.depositAmount > 0"
            + " and ((r.updatedAt is not null and r.updatedAt >= :from and r.updatedAt < :to)"
            + " or (r.updatedAt is null and r.depositPaidAt >= :from and r.depositPaidAt < :to))")
    List<Reservation> findCancelledWithDeposit(@Param("cancelled") ReservationStatus cancelled,
                                               @Param("from") Instant from,
                                               @Param("to") Instant to);
}
