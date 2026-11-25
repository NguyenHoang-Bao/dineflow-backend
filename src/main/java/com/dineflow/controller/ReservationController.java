package com.dineflow.controller;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Input;
import com.dineflow.common.Times;
import com.dineflow.entity.DiningTable;
import com.dineflow.entity.Order;
import com.dineflow.entity.Reservation;
import com.dineflow.entity.User;
import com.dineflow.enums.PaymentMethod;
import com.dineflow.enums.ReservationStatus;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.DiningTableRepository;
import com.dineflow.repository.OrderRepository;
import com.dineflow.repository.ReservationRepository;
import com.dineflow.security.CurrentUser;
import com.dineflow.security.Roles;
import com.dineflow.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

@Slf4j
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {
    /** Thời gian giữ bàn chờ khách xác nhận (đặt bàn qua app gồm 3 bước). */
    public static final int HOLD_MINUTES = 10;

    private final ReservationRepository reservations;
    private final DiningTableRepository tables;
    private final OrderRepository orders;
    private final ReservationService reservationService;
    private final TableService tableService;
    private final NotificationService notifications;
    private final MailService mailService;
    private final LineItemBuilder builder;
    private final ReservationCheckIn checkIn;
    private final Resources res;
    private final TransactionTemplate tx;
    private final com.dineflow.common.Db db;

    private Reservation find(Long id) {
        return reservations.findById(id).orElseThrow(ApiException::notFound);
    }

    /**
     * POST /api/reservations (khách hàng)
     * Mặc định đặt xong là xác nhận luôn. Gửi `hold=true` để chỉ GIỮ BÀN 10 phút, rồi gọi
     * POST /reservations/{id}/confirm; quá hạn không xác nhận thì lượt đặt tự hủy.
     */
    @Roles(UserRole.CUSTOMER)
    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@CurrentUser User user, @Body Map<String, Object> body) {
        Input in = Input.of(body);
        in.field("table_ids").required("Vui lòng chọn bàn.").arrayBetween(1, 10);
        if (in.list("table_ids") != null) {
            List<Object> ids = in.list("table_ids");
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < ids.size(); i++) {
                Long id = Input.toLong(ids.get(i));
                in.field("table_ids." + i, ids.get(i)).integer()
                        .check(seen.add(id), "Bàn bị chọn trùng.")
                        .check(v -> tables.existsById(id), "Bàn không tồn tại.");
            }
        }
        in.field("date_time").required("Vui lòng chọn thời gian.").after(Instant.now(), "Thời gian đặt bàn phải ở tương lai.");
        in.field("guest_count").required("Vui lòng nhập số khách.").intBetween(1, 100);
        in.field("email").email();
        in.field("note").string().max(500);
        in.field("hold").bool();
        boolean hasItems = in.filled("pre_order_items");
        in.field("pre_order_payment").requiredIf(hasItems, "Vui lòng chọn phương thức thanh toán món đặt trước.")
                .enumOf(PaymentMethod.class);
        builder.validate(in, "pre_order_items", false);
        in.validate();

        Instant dateTime = in.instant("date_time");
        List<Long> tableIds = in.list("table_ids").stream().map(Input::toLong).toList();
        List<Map<String, Object>> lines = hasItems ? builder.build(in.list("pre_order_items"), "pre_order_items") : List.of();

        // Dọn lượt giữ bàn quá hạn / khách trễ trước khi kiểm tra trùng lịch (ngoài transaction).
        reservationService.expireStale();

        Reservation r = tx.execute(status -> {
            List<DiningTable> ts = new ArrayList<>(tables.lockByIds(tableIds));
            ts.sort(Comparator.comparingInt(t -> tableIds.indexOf(t.getId())));
            if (ts.stream().map(DiningTable::getArea).distinct().count() > 1) {
                throw ApiException.validation("table_ids", "Các bàn ghép phải nằm trong cùng một khu vực.");
            }
            for (DiningTable t : ts) {
                String reason = tableService.unbookableReason(t, dateTime);
                if (reason != null) throw ApiException.validation("table_ids", reason);
            }
            int capacity = ts.stream().mapToInt(DiningTable::getCapacity).sum();
            int guests = in.integer("guest_count");
            if (capacity < guests) {
                throw ApiException.validation("guest_count", "Các bàn đã chọn chỉ có " + capacity + " chỗ, chưa đủ cho " + guests + " khách.");
            }
            Set<Long> taken = new LinkedHashSet<>();
            for (Reservation other : reservationService.blocking(dateTime.minus(Reservation.WINDOW_MINUTES, ChronoUnit.MINUTES),
                    dateTime.plus(Reservation.WINDOW_MINUTES, ChronoUnit.MINUTES))) {
                for (Long id : other.getTableIds()) if (tableIds.contains(id)) taken.add(id);
            }
            if (!taken.isEmpty()) {
                String names = ts.stream().filter(t -> taken.contains(t.getId())).map(DiningTable::getNumber).collect(Collectors.joining(", "));
                throw ApiException.validation("table_ids", names + " đã có người đặt trong khung giờ này.");
            }

            Reservation n = new Reservation();
            n.setCode(generateCode());
            n.setCustomer(user);
            n.setTableIds(new ArrayList<>(tableIds));
            n.setArea(ts.get(0).getArea());
            n.setDateTime(dateTime);
            n.setGuestCount(guests);
            n.setContactEmail(in.str("email"));
            n.setNote(in.str("note"));
            n.setStatus(ReservationStatus.UPCOMING);
            n.setHoldExpiresAt(in.bool("hold") ? Instant.now().plus(HOLD_MINUTES, ChronoUnit.MINUTES) : null);
            n.setPreOrderItems(lines.isEmpty() ? null : new ArrayList<>(lines));
            n.setPreOrderPayment(lines.isEmpty() ? null : PaymentMethod.of(in.str("pre_order_payment")));
            return reservations.save(n);
        });

        // Đặt thẳng (không hold) → gửi thông báo xác nhận ngay
        if (!r.isHeld()) {
            notifications.reservationConfirmed(r);
            sendConfirmationEmail(r);
        }

        return ok(res.reservation(r, false),
                r.isHeld() ? "Đã giữ bàn. Vui lòng hoàn tất trong " + HOLD_MINUTES + " phút." : "Đặt bàn thành công.", 201);
    }

    /**
     * POST /api/reservations/{id}/confirm (chủ lượt đặt)
     * Xác nhận lượt đang giữ bàn, kèm món đặt trước nếu có (gửi mảng rỗng để xóa hết món).
     * Có món đặt trước → tự tính tiền cọc 50% và ghi nhận đã thanh toán (VietQR giả lập).
     */
    @Roles(UserRole.CUSTOMER)
    @PostMapping("/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@CurrentUser User user, @PathVariable Long id,
                                                       @Body Map<String, Object> body) {
        Reservation r = find(id);
        if (!Objects.equals(r.getCustomerId(), user.getId())) {
            return error("Bạn không có quyền xác nhận lượt đặt này.", 403, null);
        }
        Input in = Input.of(body);
        boolean hasItems = in.filled("pre_order_items");
        in.field("pre_order_payment").requiredIf(hasItems, "Vui lòng chọn phương thức thanh toán món đặt trước.")
                .enumOf(PaymentMethod.class);
        builder.validate(in, "pre_order_items", false);
        in.validate();

        reservationService.expireStale();
        db.refresh(r);
        if (r.getStatus() != ReservationStatus.UPCOMING) {
            return error("Lượt giữ bàn đã hết hạn hoặc đã bị hủy. Vui lòng đặt lại.", 422, null);
        }
        if (!r.isHeld()) {
            return ok(res.reservation(r, false), "Lượt đặt bàn đã được xác nhận.");
        }

        r.setHoldExpiresAt(null);
        if (in.has("pre_order_items")) {
            List<Map<String, Object>> lines = hasItems ? builder.build(in.list("pre_order_items"), "pre_order_items") : List.of();
            r.setPreOrderItems(lines.isEmpty() ? null : new ArrayList<>(lines));
            r.setPreOrderPayment(lines.isEmpty() ? null : PaymentMethod.of(in.str("pre_order_payment")));
        }
        double deposit = Reservation.calculateDeposit(r.getPreOrderItems());
        if (deposit > 0) {
            r.setDepositAmount(BigDecimal.valueOf(deposit));
            r.setDepositPaidAt(Instant.now());
        } else {
            r.setDepositAmount(null);
            r.setDepositPaidAt(null);
        }
        reservations.save(r);
        notifications.reservationConfirmed(r);
        sendConfirmationEmail(r);
        return ok(res.reservation(r, false), "Đặt bàn thành công.");
    }

    private void sendConfirmationEmail(Reservation r) {
        try {
            List<DiningTable> ts = res.tablesOf(r);
            List<String> tableNumbers = ts.stream().map(DiningTable::getNumber).toList();
            mailService.sendReservationConfirmation(r, tableNumbers);
        } catch (Exception e) {
            log.warn("Không thể gửi email xác nhận đặt bàn #{}: {}", r.getCode(), e.getMessage());
        }
    }

    /**
     * GET /api/reservations?date=2026-11-12&status=upcoming (NV Order, Thu ngân, Admin)
     * Lịch đặt bàn trong một ngày theo giờ quán (mặc định hôm nay); không gồm lượt đang giữ bàn.
     */
    @Roles({UserRole.ORDER_STAFF, UserRole.CASHIER, UserRole.ADMIN})
    @GetMapping
    public ResponseEntity<Map<String, Object>> index(@RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("date").date();
        in.field("status").enumOf(ReservationStatus.class);
        in.validate();

        reservationService.expireStale();
        LocalDate date = in.filled("date") ? in.instant("date").atZone(Times.UTC).toLocalDate() : Times.today();
        Instant from = Times.startOfDay(date);
        List<Reservation> list = reservations.findForDay(from, Times.startOfDay(date.plusDays(1)),
                in.filled("status") ? ReservationStatus.of(in.str("status")) : null);
        return ok(list.stream().map(r -> res.reservation(r, true)).toList());
    }

    /**
     * POST /api/reservations/{id}/check-in (NV Order, Thu ngân)
     * Khách đặt trước đã tới: mở bàn, tạo đơn từ món đặt trước, lượt đặt → "Đã hoàn thành".
     */
    @Roles({UserRole.ORDER_STAFF, UserRole.CASHIER})
    @PostMapping("/{id}/check-in")
    public ResponseEntity<Map<String, Object>> checkIn(@CurrentUser User user, @PathVariable Long id) {
        Reservation r = find(id);
        // Ngoài transaction: lượt vừa bị hủy vì trễ vẫn được lưu dù bên dưới báo lỗi
        reservationService.expireStale();
        Order order = checkIn.handle(r.getId(), user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reservation", res.reservation(db.refresh(r), true));
        data.put("order", res.order(orders.findById(order.getId()).orElseThrow()));
        return ok(data, "Đã nhận bàn cho lượt đặt " + r.getCode() + ".", 201);
    }

    /** GET /api/reservations/my?status= */
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> my(@CurrentUser User user, @RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("status").enumOf(ReservationStatus.class);
        in.validate();
        reservationService.expireStale();
        List<Reservation> list = reservations.findMine(user.getId(),
                in.filled("status") ? ReservationStatus.of(in.str("status")) : null);
        return ok(list.stream().map(r -> res.reservation(r, false)).toList());
    }

    /**
     * PATCH /api/reservations/{id}/cancel
     * Khách hủy lượt của mình; NV Order / Thu ngân / Admin hủy mọi lượt "Sắp tới". Tiền cọc KHÔNG hoàn.
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@CurrentUser User user, @PathVariable Long id) {
        Reservation r = find(id);
        boolean isStaff = user.hasRole(UserRole.ORDER_STAFF, UserRole.CASHIER, UserRole.ADMIN);
        if (!isStaff && !Objects.equals(r.getCustomerId(), user.getId())) {
            return error("Bạn không có quyền hủy lượt đặt này.", 403, null);
        }
        if (r.getStatus() != ReservationStatus.UPCOMING) {
            return error("Chỉ hủy được lượt đặt đang ở trạng thái \"Sắp tới\".", 422, null);
        }
        reservationService.cancelAndNotify(r);
        String message = r.isDepositPaid()
                ? "Đã hủy lượt đặt bàn. Tiền cọc " + Times.money(r.getDepositAmount().doubleValue()) + "đ không được hoàn lại."
                : "Đã hủy lượt đặt bàn.";
        return ok(res.reservation(r, false), message);
    }

    /** Mã dạng DF-8921, không trùng. */
    private String generateCode() {
        String code;
        do {
            code = "DF-" + ThreadLocalRandom.current().nextInt(1000, 10000);
        } while (reservations.existsByCode(code));
        return code;
    }
}
