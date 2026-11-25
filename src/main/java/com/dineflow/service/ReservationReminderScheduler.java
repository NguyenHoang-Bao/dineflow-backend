package com.dineflow.service;

import com.dineflow.entity.Reservation;
import com.dineflow.enums.ReservationStatus;
import com.dineflow.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ReservationReminderScheduler {

    private final ReservationRepository reservations;
    private final NotificationService notifications;

    /**
     * Tự động chạy đúng 12:00 TRƯA mỗi ngày (theo múi giờ Việt Nam).
     * Quét tất cả các lượt đặt bàn UPCOMING trong ngày hôm đó (có giờ hẹn sau 12h)
     * và chưa được gửi thông báo trưa (reminded_noon_at IS NULL).
     */
    @Scheduled(cron = "0 0 12 * * ?", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void sendNoonDayReminders() {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(zone);
        Instant startOfDay = today.atStartOfDay(zone).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant now = Instant.now();

        List<Reservation> list = reservations.findUpcomingForNoonReminder(
                ReservationStatus.UPCOMING, startOfDay, endOfDay, now);

        if (!list.isEmpty()) {
            log.info("[Scheduler 12h] Tìm thấy {} lượt đặt bàn hôm nay cần gửi nhắc nhở trưa.", list.size());
            for (Reservation r : list) {
                try {
                    notifications.reservationDayReminder(r);
                    r.setRemindedNoonAt(Instant.now());
                    reservations.save(r);
                } catch (Exception e) {
                    log.error("[Scheduler 12h] Lỗi gửi nhắc nhở đơn #{}: {}", r.getCode(), e.getMessage());
                }
            }
        }
    }

    /**
     * Quét định kỳ 5 PHÚT một lần.
     * Tìm các lượt đặt bàn UPCOMING có giờ hẹn trong vòng 2 TIẾNG nữa (từ now đến now + 2h)
     * và chưa được gửi nhắc nhở 2 tiếng (reminded_2h_at IS NULL).
     */
    @Scheduled(fixedRate = 300000) // 5 phút = 300,000 ms
    @Transactional
    public void sendUpcoming2hReminders() {
        Instant now = Instant.now();
        Instant maxTime = now.plus(2, ChronoUnit.HOURS);

        List<Reservation> list = reservations.findUpcomingFor2hReminder(
                ReservationStatus.UPCOMING, now, maxTime);

        if (!list.isEmpty()) {
            log.info("[Scheduler 2h] Tìm thấy {} lượt đặt bàn sắp diễn ra trong 2 tiếng cần gửi nhắc nhở.", list.size());
            for (Reservation r : list) {
                try {
                    notifications.reservation2hReminder(r);
                    r.setReminded2hAt(Instant.now());
                    reservations.save(r);
                } catch (Exception e) {
                    log.error("[Scheduler 2h] Lỗi gửi nhắc nhở 2 tiếng đơn #{}: {}", r.getCode(), e.getMessage());
                }
            }
        }
    }
}
