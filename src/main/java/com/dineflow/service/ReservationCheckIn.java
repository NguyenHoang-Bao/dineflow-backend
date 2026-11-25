package com.dineflow.service;

import com.dineflow.common.ApiException;
import com.dineflow.common.Times;
import com.dineflow.entity.*;
import com.dineflow.enums.OrderStatus;
import com.dineflow.enums.PaymentStatus;
import com.dineflow.enums.ReservationStatus;
import com.dineflow.enums.TableStatus;
import com.dineflow.repository.DiningTableRepository;
import com.dineflow.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nhận bàn cho khách đã đặt trước (NV Order hoặc Thu ngân bấm khi khách tới quán).
 * 1. Lượt đặt còn "Sắp tới", đã xác nhận, đang trong khung nhận bàn (30' trước → 15' sau giờ hẹn).
 * 2. Khóa các bàn; bàn phải còn hoạt động, không khóa, không có khách khác.
 * 3. Tạo đơn gắn reservation_id + khách; chép món đặt trước vào đơn với đúng giá lúc đặt.
 * 4. Mở MỌI bàn của lượt đặt, trỏ cùng một đơn.  5. Chuyển lượt đặt sang "Đã hoàn thành".
 */
@Service
@RequiredArgsConstructor
public class ReservationCheckIn {
    private final ReservationRepository reservations;
    private final DiningTableRepository tables;
    private final OrderService orderService;
    private final TableService tableService;
    private final com.dineflow.common.Db db;

    @Transactional
    public Order handle(Long reservationId, User staff) {
        // Khóa dòng lượt đặt để hai nhân viên bấm cùng lúc không tạo hai đơn.
        Reservation r = db.lock(reservations.findById(reservationId).orElseThrow(ApiException::notFound));

        if (r.getStatus() != ReservationStatus.UPCOMING) {
            boolean lateCancelled = r.getStatus() == ReservationStatus.CANCELLED && Instant.now().isAfter(r.checkInClosesAt());
            throw ApiException.validation("reservation", lateCancelled
                    ? "Lượt đặt " + r.getCode() + " đã tự hủy vì khách trễ quá " + Reservation.GRACE_MINUTES + " phút. Hãy mở bàn như khách mới."
                    : "Lượt đặt " + r.getCode() + " đang ở trạng thái \"" + r.getStatus().label() + "\", không nhận bàn được.");
        }
        if (r.isHeld()) {
            throw ApiException.validation("reservation", "Khách chưa hoàn tất đặt bàn (đang giữ chỗ), chưa nhận bàn được.");
        }
        if (Instant.now().isBefore(r.checkInOpensAt())) {
            throw ApiException.validation("reservation", "Chưa tới giờ nhận bàn. Lượt đặt lúc " + Times.format(r.getDateTime(), "HH:mm")
                    + " nhận bàn được từ " + Times.format(r.checkInOpensAt(), "HH:mm") + ".");
        }

        List<Long> ids = r.getTableIds();
        List<DiningTable> ts = new ArrayList<>();
        for (Long id : ids) tables.findById(id).map(db::lock).ifPresent(ts::add);
        if (ts.size() != ids.size()) {
            throw ApiException.validation("reservation", "Một số bàn của lượt đặt không còn tồn tại.");
        }
        for (DiningTable t : ts) {
            if (!t.getIsActive() || t.getIsLocked()) {
                throw ApiException.validation("reservation", t.getNumber() + " đang tạm khóa hoặc ngừng phục vụ. Hãy mở khóa hoặc đổi bàn cho khách.");
            }
            if (t.getStatus() == TableStatus.SERVING) {
                throw ApiException.validation("reservation", t.getNumber() + " vẫn đang có khách. Hãy thanh toán hoặc chuyển khách đang ngồi sang bàn khác rồi nhận bàn.");
            }
        }

        Order order = new Order();
        order.setTable(ts.get(0));
        order.setCustomer(r.getCustomer());
        order.setStaff(staff);
        order.setReservation(r);
        order.setStatus(OrderStatus.PLACED);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setNote(r.getNote());

        // Giữ nguyên giá lúc đặt vì tiền cọc 50% đã tính trên giá này.
        if (r.getPreOrderItems() != null) {
            for (Map<String, Object> line : r.getPreOrderItems()) {
                OrderItem item = new OrderItem();
                item.setFoodId(line.get("food_id") == null ? null : ((Number) line.get("food_id")).longValue());
                item.setFoodName((String) line.get("food_name"));
                item.setQuantity(((Number) line.get("quantity")).intValue());
                item.setPrice(BigDecimal.valueOf(((Number) line.get("price")).doubleValue()));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> opts = (List<Map<String, Object>>) line.getOrDefault("options", List.of());
                item.setOptions(opts == null ? new ArrayList<>() : new ArrayList<>(opts));
                item.setNote((String) line.get("note"));
                order.addItem(item);
            }
        }
        order.recalculateTotal();
        orderService.create(order);

        for (DiningTable t : ts) tableService.occupy(t, order);

        r.setStatus(ReservationStatus.COMPLETED);
        reservations.save(r);
        return order;
    }
}
