package com.dineflow.service;

import com.dineflow.common.Times;
import com.dineflow.entity.AppNotification;
import com.dineflow.entity.Reservation;
import com.dineflow.entity.User;
import com.dineflow.repository.AppNotificationRepository;
import com.dineflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** Tạo nhanh thông báo in-app cho khách và tự động gửi Push Notification qua Firebase FCM. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final AppNotificationRepository repo;
    private final UserRepository userRepository;
    private final FcmService fcmService;

    private AppNotification create(Long customerId, String type, String title, String body, Long reservationId) {
        AppNotification n = new AppNotification();
        n.setCustomerId(customerId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setData(Map.of("reservation_id", reservationId));
        AppNotification saved = repo.save(n);

        // Tự động Push Notification qua Firebase tới máy khách hàng
        try {
            if (customerId != null) {
                User user = userRepository.findById(customerId).orElse(null);
                if (user != null && user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
                    Map<String, String> data = new HashMap<>();
                    data.put("type", type);
                    data.put("notification_id", String.valueOf(saved.getId()));
                    if (reservationId != null) {
                        data.put("reservation_id", String.valueOf(reservationId));
                    }
                    fcmService.sendPush(user.getFcmToken(), title, body, data);
                }
            }
        } catch (Exception e) {
            log.warn("[FCM] Bỏ qua lỗi gửi push: {}", e.getMessage());
        }

        return saved;
    }

    private static String when(Reservation r) {
        return Times.format(r.getDateTime(), "HH:mm dd/MM/yyyy");
    }

    public void reservationConfirmed(Reservation r) {
        create(r.getCustomerId(), "reservation_confirmed", "Đặt bàn thành công",
                "Lượt đặt bàn của bạn vào " + when(r) + " đã được xác nhận.", r.getId());
    }

    /** Nhắc nhở đúng 12:00 trưa ngày hẹn */
    public void reservationDayReminder(Reservation r) {
        String timeStr = Times.format(r.getDateTime(), "HH:mm");
        create(r.getCustomerId(), "reservation_reminder_noon", "Nhắc lịch hẹn hôm nay 🍽️",
                "Hôm nay bạn có lịch đặt bàn tại DineFlow lúc " + timeStr + ". Hẹn gặp bạn tại nhà hàng nhé!", r.getId());
    }

    /** Nhắc nhở trước 2 tiếng trước giờ hẹn */
    public void reservation2hReminder(Reservation r) {
        String timeStr = Times.format(r.getDateTime(), "HH:mm");
        create(r.getCustomerId(), "reservation_reminder_2h", "Sắp đến giờ hẹn (còn 2 tiếng) ⏰",
                "Lịch đặt bàn của bạn lúc " + timeStr + " sẽ diễn ra trong 2 tiếng nữa. Vui lòng chuẩn bị và có mặt đúng giờ nhé!", r.getId());
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
