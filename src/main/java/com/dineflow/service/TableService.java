package com.dineflow.service;

import com.dineflow.common.Times;
import com.dineflow.entity.DiningTable;
import com.dineflow.entity.Order;
import com.dineflow.entity.Reservation;
import com.dineflow.enums.TableStatus;
import com.dineflow.repository.DiningTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Các hàm nghiệp vụ của bàn (Model DiningTable bên PHP). */
@Service
@RequiredArgsConstructor
public class TableService {
    private final DiningTableRepository tables;
    private final ReservationService reservations;

    /**
     * Bàn có nhận khách đặt trước vào thời điểm at không (chưa xét trùng lượt đặt khác).
     * - Bàn bị vô hiệu hóa hoặc đang khóa: không nhận.
     * - Bàn đang có khách: chỉ chặn khi giờ đặt nằm trong WINDOW_MINUTES phút tới.
     */
    public boolean isBookableAt(DiningTable t, Instant at) {
        if (!t.getIsActive() || t.getIsLocked()) return false;
        boolean servingNow = t.getStatus() == TableStatus.SERVING;
        return !(servingNow && at.isBefore(Instant.now().plus(Reservation.WINDOW_MINUTES, ChronoUnit.MINUTES)));
    }

    /** Lý do bàn không nhận đặt; null nếu đặt được. */
    public String unbookableReason(DiningTable t, Instant at) {
        if (!t.getIsActive()) return t.getNumber() + " đã ngừng phục vụ.";
        if (t.getIsLocked()) return t.getNumber() + " đang tạm khóa.";
        if (!isBookableAt(t, at)) {
            String from = Times.format(Instant.now().plus(Reservation.WINDOW_MINUTES, ChronoUnit.MINUTES), "HH:mm");
            return t.getNumber() + " đang có khách, chỉ nhận đặt từ " + from + " trở đi.";
        }
        return null;
    }

    /**
     * Lượt đặt sớm nhất đang "giữ" bàn này: còn hiệu lực và giờ hẹn trong
     * [bây giờ − GRACE_MINUTES, bây giờ + WINDOW_MINUTES]. Null nếu không có.
     */
    public Reservation heldReservation(DiningTable t) {
        Instant now = Instant.now();
        return reservations.blocking(now.minus(Reservation.GRACE_MINUTES, ChronoUnit.MINUTES),
                        now.plus(Reservation.WINDOW_MINUTES, ChronoUnit.MINUTES))
                .stream().filter(r -> r.getTableIds().contains(t.getId()))
                .findFirst().orElse(null);
    }

    /** Lý do không mở bàn này cho khách vãng lai (null nếu mở được). Gọi expireStale() trước. */
    public String walkInBlockReason(DiningTable t) {
        Reservation r = heldReservation(t);
        return r == null ? null
                : t.getNumber() + " có khách đặt lúc " + Times.format(r.getDateTime(), "HH:mm") + " (mã " + r.getCode() + "). "
                + "Hãy chọn bàn khác, hoặc vào Lịch đặt bàn để nhận bàn cho khách đặt.";
    }

    /** Gắn đơn hàng đang phục vụ vào bàn. */
    public void occupy(DiningTable t, Order order) {
        t.setStatus(TableStatus.SERVING);
        if (t.getServingSince() == null) t.setServingSince(Instant.now());
        t.setCurrentOrderId(order.getId());
        tables.save(t);
    }

    /** Giải phóng bàn về trạng thái trống. */
    public void release(DiningTable t) {
        t.setStatus(TableStatus.EMPTY);
        t.setServingSince(null);
        t.setCurrentOrderId(null);
        tables.save(t);
    }
}
