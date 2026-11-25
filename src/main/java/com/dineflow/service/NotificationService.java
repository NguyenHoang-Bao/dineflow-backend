package com.dineflow.service;

import com.dineflow.common.Times;
import com.dineflow.entity.AppNotification;
import com.dineflow.entity.Reservation;
import com.dineflow.repository.AppNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Tạo nhanh thông báo in-app cho khách (các helper tĩnh trong Model AppNotification bên PHP). */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final AppNotificationRepository repo;

    private AppNotification create(Long customerId, String type, String title, String body, Long reservationId) {
        AppNotification n = new AppNotification();
        n.setCustomerId(customerId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setData(Map.of("reservation_id", reservationId));
        return repo.save(n);
    }

    private static String when(Reservation r) {
        return Times.format(r.getDateTime(), "HH:mm dd/MM/yyyy");
    }

    public void reservationConfirmed(Reservation r) {
        create(r.getCustomerId(), "reservation_confirmed", "Đặt bàn thành công",
                "Lượt đặt bàn của bạn vào " + when(r) + " đã được xác nhận.", r.getId());
    }

    public void reservationCancelled(Reservation r) {
        create(r.getCustomerId(), "reservation_cancelled", "Đặt bàn đã bị hủy",
                "Lượt đặt bàn của bạn vào " + when(r) + " đã bị hủy.", r.getId());
    }

    /** Lượt đặt bị hủy do khách trễ quá GRACE_MINUTES phút. */
    public void reservationNoShow(Reservation r) {
        String deposit = r.isDepositPaid()
                ? " Tiền cọc " + Times.money(r.getDepositAmount().doubleValue()) + "đ không được hoàn lại."
                : "";
        create(r.getCustomerId(), "reservation_cancelled", "Lượt đặt bàn bị hủy do quá giờ",
                "Lượt đặt bàn lúc " + when(r) + " đã bị hủy vì bạn chưa đến sau "
                        + Reservation.GRACE_MINUTES + " phút." + deposit, r.getId());
    }
}
