package com.dineflow.controller;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Input;
import com.dineflow.common.Times;
import com.dineflow.entity.Invoice;
import com.dineflow.entity.Shift;
import com.dineflow.entity.User;
import com.dineflow.enums.PaymentMethod;
import com.dineflow.enums.ShiftStatus;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.InvoiceRepository;
import com.dineflow.repository.ShiftRepository;
import com.dineflow.security.CurrentUser;
import com.dineflow.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

/**
 * Ca làm việc của thu ngân.
 * POST /api/shifts (mở ca) · GET /api/shifts/current · PATCH /api/shifts/{id}/close · GET /api/shifts?date= (Admin)
 */
@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class ShiftController {
    private final ShiftRepository shifts;
    private final InvoiceRepository invoices;

    /** GET /api/shifts?date=2026-11-12 (Admin) — các ca MỞ trong một ngày, mới nhất trước. */
    @Roles(UserRole.ADMIN)
    @GetMapping
    public ResponseEntity<Map<String, Object>> index(@RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("date").dateFormatYmd();
        in.validate();
        // whereDate theo giờ DB (UTC), giống bản PHP
        LocalDate d = in.filled("date") ? Times.parseDate(in.str("date")) : LocalDate.now(Times.UTC);
        List<Shift> list = shifts.findOpenedBetween(d.atStartOfDay(Times.UTC).toInstant(), d.plusDays(1).atStartOfDay(Times.UTC).toInstant());
        return ok(list.stream().map(this::format).toList());
    }

    @Roles({UserRole.CASHIER, UserRole.ADMIN})
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> current(@CurrentUser User user) {
        return shifts.findFirstByCashier_IdAndStatus(user.getId(), ShiftStatus.OPEN)
                .map(s -> ok(format(s), "Ca đang mở."))
                .orElseGet(() -> ok(null, "Chưa có ca nào đang mở."));
    }

    /** Mở ca mới. Body: { "opening_cash": 500000 } */
    @Roles({UserRole.CASHIER, UserRole.ADMIN})
    @PostMapping
    public ResponseEntity<Map<String, Object>> open(@CurrentUser User user, @Body Map<String, Object> body) {
        Input in = Input.of(body);
        in.field("opening_cash").required("Vui lòng nhập tiền đầu ca.").numMin(0);
        in.validate();

        if (shifts.findFirstByCashier_IdAndStatus(user.getId(), ShiftStatus.OPEN).isPresent()) {
            throw ApiException.validation("shift", "Bạn đang có ca làm việc chưa đóng. Vui lòng đóng ca hiện tại trước.");
        }
        Shift s = new Shift();
        s.setCashier(user);
        s.setStatus(ShiftStatus.OPEN);
        s.setOpeningCash(BigDecimal.valueOf(in.num("opening_cash")));
        s.setOpenedAt(Instant.now());
        shifts.save(s);
        return ok(format(s), "Mở ca thành công.", 201);
    }

    /** Đóng ca: nhập tiền mặt đếm được, hệ thống tính chênh lệch. Body: { "closing_cash_counted": 1200000 } */
    @Roles({UserRole.CASHIER, UserRole.ADMIN})
    @PatchMapping("/{id}/close")
    public ResponseEntity<Map<String, Object>> close(@CurrentUser User user, @PathVariable Long id,
                                                     @Body Map<String, Object> body) {
        Shift s = shifts.findById(id).orElseThrow(ApiException::notFound);
        if (!Objects.equals(s.getCashierId(), user.getId())) return error("Bạn không có quyền đóng ca này.", 403, null);
        if (!s.isOpen()) throw ApiException.validation("shift", "Ca này đã đóng.");

        Input in = Input.of(body);
        in.field("closing_cash_counted").required("Vui lòng nhập tiền mặt đếm được.").numMin(0);
        in.validate();

        s.setStatus(ShiftStatus.CLOSED);
        s.setClosingCashCounted(BigDecimal.valueOf(in.num("closing_cash_counted")));
        s.setClosedAt(Instant.now());
        shifts.save(s);
        return ok(format(s), "Đóng ca thành công.");
    }

    /** Báo cáo ca: doanh thu theo phương thức, tiền mặt phải có, chênh lệch. */
    private Map<String, Object> format(Shift s) {
        List<Invoice> list = s.getId() == null ? List.of() : invoices.findByShift_Id(s.getId());
        Map<String, Object> byMethod = new LinkedHashMap<>();
        double totalRevenue = 0, cashRevenue = 0;
        for (PaymentMethod m : PaymentMethod.values()) {
            double sum = list.stream().filter(i -> i.getPaymentMethod() == m).mapToDouble(i -> i.getTotal().doubleValue()).sum();
            byMethod.put(m.value(), sum);
            totalRevenue += sum;
            if (m == PaymentMethod.CASH) cashRevenue = sum;
        }
        // Tiền mặt phải có = đầu ca + thu tiền mặt trong ca
        double expectedCash = s.getOpeningCash().doubleValue() + cashRevenue;
        Double counted = s.getClosingCashCounted() == null ? null : s.getClosingCashCounted().doubleValue();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("cashier_id", s.getCashierId());
        m.put("cashier_name", s.getCashier() == null ? "" : s.getCashier().getName());
        m.put("status", s.getStatus().value());
        m.put("opening_cash", s.getOpeningCash().doubleValue());
        m.put("closing_cash_counted", counted);
        m.put("expected_cash", expectedCash);
        m.put("cash_difference", counted == null ? null : counted - expectedCash);
        m.put("revenue_by_method", byMethod);
        m.put("total_revenue", totalRevenue);
        m.put("invoice_count", list.size());
        m.put("opened_at", Times.iso(s.getOpenedAt()));
        m.put("closed_at", Times.iso(s.getClosedAt()));
        return m;
    }
}
