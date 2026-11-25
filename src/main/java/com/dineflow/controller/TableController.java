package com.dineflow.controller;

import com.dineflow.common.Input;
import com.dineflow.common.Times;
import com.dineflow.entity.DiningTable;
import com.dineflow.entity.Reservation;
import com.dineflow.enums.TableStatus;
import com.dineflow.repository.DiningTableRepository;
import com.dineflow.service.ReservationService;
import com.dineflow.service.Resources;
import com.dineflow.service.TableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.dineflow.common.ApiResponse.ok;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TableController {
    private final DiningTableRepository tables;
    private final ReservationService reservations;
    private final TableService tableService;
    private final Resources res;

    /**
     * GET /api/tables?area=&status=
     * Bàn đang trống nhưng có lượt đặt trong [bây giờ − 15 phút, bây giờ + 90 phút] được báo "reserved"
     * (chỉ trong phản hồi, không đổi DB).
     */
    @GetMapping("/tables")
    public ResponseEntity<Map<String, Object>> index(@RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("area").max(50);
        in.field("status").enumOf(TableStatus.class);
        in.validate();

        reservations.expireStale();

        // Lượt đặt sớm nhất đang giữ mỗi bàn
        Map<Long, Reservation> soon = new HashMap<>();
        Instant now = Instant.now();
        for (Reservation r : reservations.blocking(now.minus(Reservation.GRACE_MINUTES, ChronoUnit.MINUTES),
                now.plus(Reservation.WINDOW_MINUTES, ChronoUnit.MINUTES))) {
            for (Long id : r.getTableIds()) soon.putIfAbsent(id, r);
        }

        List<DiningTable> list = in.filled("area") ? tables.findByAreaOrderByIdAsc(in.str("area")) : tables.findAllByOrderByIdAsc();
        List<Map<String, Object>> out = new ArrayList<>();
        for (DiningTable t : list) {
            Reservation r = soon.get(t.getId());
            Map<String, Object> extra = new HashMap<>();
            extra.put("next_reservation_at", r == null ? null : Times.iso(r.getDateTime()));
            if (r != null) {
                Map<String, Object> nr = new LinkedHashMap<>();
                nr.put("id", r.getId());
                nr.put("code", r.getCode());
                nr.put("date_time", Times.iso(r.getDateTime()));
                nr.put("guest_count", r.getGuestCount());
                nr.put("customer_name", r.getCustomer() == null ? null : r.getCustomer().getName());
                nr.put("customer_phone", r.getCustomer() == null ? null : r.getCustomer().getPhone());
                nr.put("deposit_amount", r.isDepositPaid() ? r.getDepositAmount().doubleValue() : null);
                extra.put("next_reservation", nr);
            }
            String effective = (t.isEmpty() && r != null) ? TableStatus.RESERVED.value() : t.getStatus().value();
            extra.put("effective_status", effective);
            if (in.filled("status") && !effective.equals(in.str("status"))) continue;
            out.add(res.table(t, extra));
        }
        return ok(out, "Thành công", 200, Map.of("areas", tables.findAreas()));
    }

    /**
     * GET /api/tables/available?datetime=2026-11-13T19:00:00
     * Tất cả bàn kèm `available` cho thời điểm đặt bàn cụ thể (cùng điều kiện với POST /reservations).
     */
    @GetMapping("/tables/available")
    public ResponseEntity<Map<String, Object>> available(@RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("datetime").required("Vui lòng chọn thời gian.").date();
        in.validate();

        reservations.expireStale();
        Instant dt = in.instant("datetime");

        Set<Long> booked = new HashSet<>();
        for (Reservation r : reservations.blocking(dt.minus(Reservation.WINDOW_MINUTES, ChronoUnit.MINUTES),
                dt.plus(Reservation.WINDOW_MINUTES, ChronoUnit.MINUTES))) {
            booked.addAll(r.getTableIds());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (DiningTable t : tables.findAllByOrderByIdAsc()) {
            Map<String, Object> m = res.table(t);
            m.put("available", tableService.isBookableAt(t, dt) && !booked.contains(t.getId()));
            out.add(m);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("areas", tables.findAreas());
        meta.put("datetime", Times.iso(dt));
        return ok(out, "Thành công", 200, meta);
    }
}
