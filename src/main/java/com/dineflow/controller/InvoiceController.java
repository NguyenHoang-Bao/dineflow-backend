package com.dineflow.controller;

import com.dineflow.common.Input;
import com.dineflow.common.Times;
import com.dineflow.entity.Invoice;
import com.dineflow.entity.User;
import com.dineflow.enums.InvoiceStatus;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.InvoiceRepository;
import com.dineflow.security.CurrentUser;
import com.dineflow.security.Roles;
import com.dineflow.service.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static com.dineflow.common.ApiResponse.ok;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InvoiceController {
    private final InvoiceRepository invoices;
    private final Resources res;

    /**
     * GET /api/invoices?date=2025-01-31&mine=1&per_page=50 (thu ngân, admin)
     * Hóa đơn đã thanh toán, mới nhất trước. mine=1: chỉ hóa đơn do chính người đang đăng nhập thu.
     */
    @Roles({UserRole.CASHIER, UserRole.ADMIN})
    @GetMapping("/invoices")
    public ResponseEntity<Map<String, Object>> index(@CurrentUser User user, @RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("date").date();
        in.field("mine").bool();
        in.field("per_page").intBetween(1, 100);
        in.validate();

        Instant from = null, to = null;
        if (in.filled("date")) { // whereDate theo giờ DB (UTC)
            LocalDate d = in.instant("date").atZone(Times.UTC).toLocalDate();
            from = d.atStartOfDay(Times.UTC).toInstant();
            to = d.plusDays(1).atStartOfDay(Times.UTC).toInstant();
        }
        int perPage = in.filled("per_page") ? in.integer("per_page") : 50;
        int page = Math.max(1, Optional.ofNullable(Input.toLong(query.get("page"))).orElse(1L).intValue());
        Page<Invoice> result = invoices.search(InvoiceStatus.PAID, from, to, in.bool("mine") ? user.getId() : null,
                PageRequest.of(page - 1, perPage));

        return ok(result.getContent().stream().map(res::invoice).toList(), "Thành công", 200,
                OrderController.pageMeta(result, page, perPage));
    }
}
