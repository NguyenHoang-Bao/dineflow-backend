package com.dineflow.service;

import com.dineflow.common.ApiException;
import com.dineflow.common.Times;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Kỳ báo cáo khi xuất Excel: một NGÀY, một THÁNG hoặc một NĂM — tính theo giờ của quán.
 * Chỉ cho chọn những tháng / năm ĐÃ CÓ doanh thu; ngày thì chọn ngày bất kỳ tới hôm nay.
 */
public final class ReportPeriod {
    public static final Map<String, String> TYPES = new LinkedHashMap<>(Map.of("day", "Ngày", "month", "Tháng", "year", "Năm"));

    public final String type;
    public final String value;
    /** Đầu kỳ (giờ quán). */
    public final LocalDate start;
    /** Hết kỳ, không tính (giờ quán). */
    public final LocalDate end;
    public final String label;

    private ReportPeriod(String type, String value, LocalDate start, LocalDate end, String label) {
        this.type = type;
        this.value = value;
        this.start = start;
        this.end = end;
        this.label = label;
    }

    /** @param paidMoments thời điểm thanh toán của mọi hóa đơn (để biết tháng/năm nào có doanh thu) */
    public static ReportPeriod make(String type, String value, List<Instant> paidMoments) {
        value = value == null ? "" : value.trim();
        switch (type == null ? "" : type) {
            case "day" -> {
                LocalDate d = Times.parseDate(value);
                if (d == null || !d.toString().equals(value)) throw fail("Ngày không hợp lệ.");
                if (d.isAfter(Times.today())) throw fail("Không xuất được báo cáo cho ngày trong tương lai.");
                return new ReportPeriod("day", d.toString(), d, d.plusDays(1),
                        "Ngày " + d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            }
            case "month" -> {
                if (!availableMonths(paidMoments).containsKey(value)) throw fail("Tháng này chưa có doanh thu để xuất.");
                YearMonth ym = YearMonth.parse(value);
                LocalDate s = ym.atDay(1);
                return new ReportPeriod("month", value, s, s.plusMonths(1),
                        "Tháng " + ym.format(DateTimeFormatter.ofPattern("MM/yyyy")));
            }
            case "year" -> {
                if (!availableYears(paidMoments).containsKey(value)) throw fail("Năm này chưa có doanh thu để xuất.");
                LocalDate s = LocalDate.of(Integer.parseInt(value), 1, 1);
                return new ReportPeriod("year", value, s, s.plusYears(1), "Năm " + value);
            }
            default -> throw fail("Kỳ báo cáo phải là ngày, tháng hoặc năm.");
        }
    }

    public Instant startForDb() {
        return Times.startOfDay(start);
    }

    public Instant endForDb() {
        return Times.startOfDay(end);
    }

    /** Tên file tải về, vd bao-cao-dineflow_thang-2026-11.xlsx */
    public String fileName() {
        String prefix = Map.of("day", "ngay", "month", "thang", "year", "nam").get(type);
        return "bao-cao-dineflow_" + prefix + "-" + value + ".xlsx";
    }

    /** Các tháng có hóa đơn đã thanh toán, mới nhất trước: {"2026-11": "Tháng 11/2026"}. */
    public static Map<String, String> availableMonths(List<Instant> paidMoments) {
        TreeMap<String, String> m = new TreeMap<>(Comparator.reverseOrder());
        for (Instant at : paidMoments) {
            ZonedDateTime z = Times.local(at);
            m.put(String.format("%d-%02d", z.getYear(), z.getMonthValue()),
                    String.format("Tháng %02d/%d", z.getMonthValue(), z.getYear()));
        }
        return new LinkedHashMap<>(m);
    }

    public static Map<String, String> availableYears(List<Instant> paidMoments) {
        TreeMap<String, String> m = new TreeMap<>(Comparator.reverseOrder());
        for (Instant at : paidMoments) {
            int y = Times.local(at).getYear();
            m.put(String.valueOf(y), "Năm " + y);
        }
        return new LinkedHashMap<>(m);
    }

    private static ApiException fail(String message) {
        return ApiException.validation("period", message);
    }
}
