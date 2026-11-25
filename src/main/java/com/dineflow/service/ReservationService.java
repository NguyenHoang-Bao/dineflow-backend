package com.dineflow.service;

import com.dineflow.entity.Reservation;
import com.dineflow.enums.ReservationStatus;
import com.dineflow.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationRepository repo;
    private final NotificationService notifications;

    /**
     * Dọn mọi lượt đặt không còn hiệu lực trước khi đọc / kiểm tra xung đột:
     * lượt giữ bàn quá hạn và lượt khách đến trễ quá GRACE_MINUTES phút.
     * Gọi TRƯỚC transaction chính để việc dọn vẫn được lưu dù thao tác sau đó báo lỗi.
     */
    @Transactional
    public void expireStale() {
        Instant now = Instant.now();
        // Sửa qua entity (không UPDATE hàng loạt) để dữ liệu đã đọc trong request này cũng được cập nhật
        for (Reservation r : repo.findLapsedHolds(ReservationStatus.UPCOMING, now)) {
            r.setStatus(ReservationStatus.CANCELLED);
        }

        List<Reservation> lapsed = repo.findNoShows(ReservationStatus.UPCOMING,
                now.minus(Reservation.GRACE_MINUTES, ChronoUnit.MINUTES));
        for (Reservation r : lapsed) {
            // Chỉ request nào đổi được dòng này mới gửi thông báo → không gửi trùng
            int changed = repo.cancelIfUpcoming(r.getId(), ReservationStatus.UPCOMING, ReservationStatus.CANCELLED);
            r.setStatus(ReservationStatus.CANCELLED);
            if (changed > 0) notifications.reservationNoShow(r);
        }
    }

    /** Lượt đặt đang chiếm bàn với giờ hẹn trong [from, to], sớm nhất trước. */
    public List<Reservation> blocking(Instant from, Instant to) {
        return repo.findBlocking(ReservationStatus.UPCOMING, Instant.now(), from, to);
    }

    /**
     * Hủy lượt đặt "Sắp tới" và báo cho khách. Tiền cọc KHÔNG hoàn (giữ nguyên deposit_amount,
     * deposit_paid_at làm bằng chứng đối soát).
     */
    @Transactional
    public void cancelAndNotify(Reservation r) {
        r.setStatus(ReservationStatus.CANCELLED);
        r.setHoldExpiresAt(null);
        repo.save(r);
        notifications.reservationCancelled(r);
    }
}
