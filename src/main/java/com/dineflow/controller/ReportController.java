package com.dineflow.controller;

import com.dineflow.common.Input;
import com.dineflow.common.Times;
import com.dineflow.entity.Invoice;
import com.dineflow.enums.InvoiceStatus;
import com.dineflow.enums.OrderStatus;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.InvoiceRepository;
import com.dineflow.repository.OrderItemRepository;
import com.dineflow.repository.OrderRepository;
import com.dineflow.security.Roles;
import com.dineflow.service.ReportExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

/** Báo cáo (Admin). Ngày / tuần / tháng tính theo giờ của quán; doanh thu = thực thu + tiền cọc. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Roles(UserRole.ADMIN)
public class ReportController {
    private static final int TOP_FOODS_DAYS = 30;
    private static final int TOP_FOODS_LIMIT = 10;
    private static final int MAX_RANGE_DAYS = 31;
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");

    private final InvoiceRepository invoices;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;

    /** GET /api/reports/overview — số liệu cho Dashboard. */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        ZonedDateTime now = ZonedDateTime.now(Times.tz());
        ZonedDateTime today = now.toLocalDate().atStartOfDay(Times.tz());
        ZonedDateTime thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        ZonedDateTime firstMonth = today.withDayOfMonth(1).minusMonths(5);
        ZonedDateTime firstWeek = thisWeek.minusWeeks(5);
        ZonedDateTime firstDay = today.minusDays(6);

        List<Invoice> list = invoices.findPaidBetween(InvoiceStatus.PAID, firstMonth.toInstant(), FAR_FUTURE);
        Sum sum = (from, to) -> Invoice.sumRevenue(list.stream()
                .filter(i -> !i.getPaidAt().isBefore(from.toInstant()) && (to == null || i.getPaidAt().isBefore(to.toInstant())))
                .toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("today_revenue", sum.of(today, null));
        data.put("yesterday_revenue", sum.of(today.minusDays(1), today));
        data.put("week_revenue", sum.of(thisWeek, null));
        data.put("last_week_revenue", sum.of(thisWeek.minusWeeks(1), thisWeek));
        data.put("today_orders", orders.countCreatedSince(today.toInstant(), OrderStatus.CANCELLED));
        // Chỉ đếm được khách có tài khoản; đơn gọi tại bàn không gắn khách.
        data.put("today_members", orders.countDistinctCustomersSince(today.toInstant(), OrderStatus.CANCELLED));
        data.put("revenue_by_day", series(6, firstDay, d -> d.plusDays(1), ReportController::weekdayLabel, sum));
        data.put("revenue_by_week", series(5, firstWeek, d -> d.plusWeeks(1), d -> d.format(DM), sum));
        data.put("revenue_by_month", series(5, firstMonth, d -> d.plusMonths(1), d -> "T" + d.getMonthValue(), sum));
        data.put("top_foods_days", TOP_FOODS_DAYS);
        List<Map<String, Object>> top = ReportExporter.topFoods(orderItems.findPaidBetween(InvoiceStatus.PAID,
                today.minusDays(TOP_FOODS_DAYS - 1).toInstant(), FAR_FUTURE));
        data.put("top_foods", top.subList(0, Math.min(TOP_FOODS_LIMIT, top.size())));
        return ok(data);
    }

    /** GET /api/reports/revenue?from=2026-10-01&to=2026-10-15 — doanh thu từng ngày (tối đa 31 ngày). */
    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> revenue(@RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("from").required("Vui lòng chọn ngày bắt đầu.").dateFormatYmd();
        in.field("to").required("Vui lòng chọn ngày kết thúc.").dateFormatYmd()
                .afterOrEqualDate(Times.parseDate(in.str("from")), "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        in.validate();

        LocalDate fromDay = Times.parseDate(in.str("from"));
        LocalDate toDay = Times.parseDate(in.str("to"));
        if (toDay.isAfter(Times.today())) return error("Không chọn được ngày trong tương lai.", 422, null);
        int days = (int) ChronoUnit.DAYS.between(fromDay, toDay) + 1;
        if (days > MAX_RANGE_DAYS) return error("Chỉ xem được tối đa " + MAX_RANGE_DAYS + " ngày mỗi lần.", 422, null);

        ZonedDateTime from = fromDay.atStartOfDay(Times.tz());
        ZonedDateTime to = toDay.atStartOfDay(Times.tz());
        List<Invoice> list = invoices.findPaidBetween(InvoiceStatus.PAID, from.toInstant(), to.plusDays(1).toInstant());
        Sum sum = (a, b) -> Invoice.sumRevenue(list.stream()
                .filter(i -> !i.getPaidAt().isBefore(a.toInstant()) && (b == null || i.getPaidAt().isBefore(b.toInstant())))
                .toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", fromDay.toString());
        data.put("to", toDay.toString());
        data.put("total", Invoice.sumRevenue(list));
        data.put("revenue_by_day", series(days - 1, from, d -> d.plusDays(1), d -> d.format(DM), sum));
        return ok(data);
    }

    @FunctionalInterface
    interface Sum {
        double of(ZonedDateTime from, ZonedDateTime to);
    }

    /** Chuỗi doanh thu count + 1 mốc liên tiếp, cũ nhất trước: [{label, amount}]. */
    private static List<Map<String, Object>> series(int count, ZonedDateTime first, Function<ZonedDateTime, ZonedDateTime> next,
                                                    Function<ZonedDateTime, String> label, Sum sum) {
        List<Map<String, Object>> points = new ArrayList<>();
        ZonedDateTime from = first;
        for (int i = 0; i <= count; i++) {
            ZonedDateTime to = next.apply(from);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("label", label.apply(from));
            p.put("amount", sum.of(from, to));
            points.add(p);
            from = to;
        }
        return points;
    }

    private static String weekdayLabel(ZonedDateTime d) {
        int iso = d.getDayOfWeek().getValue();
        return iso == 7 ? "CN" : "T" + (iso + 1);
    }
}
