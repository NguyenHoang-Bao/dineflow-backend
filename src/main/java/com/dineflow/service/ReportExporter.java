package com.dineflow.service;

import com.dineflow.common.Times;
import com.dineflow.entity.Invoice;
import com.dineflow.entity.OrderItem;
import com.dineflow.enums.InvoiceStatus;
import com.dineflow.repository.InvoiceRepository;
import com.dineflow.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Xuất báo cáo doanh thu ra Excel (.xlsx) theo một kỳ (ngày / tháng / năm).
 * Các sheet: Hóa đơn, Doanh thu theo ngày, Doanh thu theo tháng (chỉ khi xuất NĂM), Món bán chạy.
 * Doanh thu = Invoice.revenueAmount() (thực thu tại quầy + tiền cọc).
 */
@Service
@RequiredArgsConstructor
public class ReportExporter {
    private final InvoiceRepository invoices;
    private final OrderItemRepository orderItems;

    @Transactional(readOnly = true)
    public byte[] export(ReportPeriod period) throws Exception {
        List<Invoice> list = invoices.findPaidBetween(InvoiceStatus.PAID, period.startForDb(), period.endForDb());
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeInvoices(wb.createSheet("Hóa đơn"), list, period);
            writeRevenueByDay(wb.createSheet("Doanh thu theo ngày"), list, period);
            if ("year".equals(period.type)) writeRevenueByMonth(wb.createSheet("Doanh thu theo tháng"), list, period);
            writeTopFoods(wb.createSheet("Món bán chạy"), period);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void row(Sheet sheet, Object... values) {
        Row r = sheet.createRow(sheet.getPhysicalNumberOfRows());
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            if (v instanceof Number n) r.createCell(i).setCellValue(n.doubleValue());
            else r.createCell(i).setCellValue(v == null ? "" : v.toString());
        }
    }

    private static LocalDate localDay(Invoice i) {
        return Times.local(i.getPaidAt()).toLocalDate();
    }

    private void writeInvoices(Sheet sheet, List<Invoice> list, ReportPeriod period) {
        row(sheet, "Báo cáo doanh thu DineFlow – " + period.label);
        row(sheet, "Mã hóa đơn", "Mã đơn", "Thời gian thanh toán", "Bàn", "Thu ngân",
                "Tạm tính", "Giảm giá", "Mã khuyến mãi", "Tiền cọc (trả khi đặt bàn)", "Thực thu tại quầy",
                "Doanh thu (gồm cọc)", "Phương thức");
        double sub = 0, disc = 0, dep = 0, tot = 0;
        for (Invoice i : list) {
            var o = i.getOrder();
            row(sheet,
                    String.format("HD%05d", i.getId()),
                    o == null ? "" : o.getCode(),
                    i.getPaidAt() == null ? "" : Times.format(i.getPaidAt(), "dd/MM/yyyy HH:mm"),
                    o != null && o.getTable() != null ? o.getTable().getNumber() : "Đặt qua app",
                    i.getCashier() == null ? "" : i.getCashier().getName(),
                    i.getSubtotal(), i.getDiscount(),
                    i.getPromotion() == null ? "" : i.getPromotion().getCode(),
                    i.getDepositAmount() == null ? 0 : i.getDepositAmount(),
                    i.getTotal(), i.revenueAmount(),
                    i.getPaymentMethod() == null ? "" : i.getPaymentMethod().label());
            sub += i.getSubtotal().doubleValue();
            disc += i.getDiscount().doubleValue();
            dep += i.getDepositAmount() == null ? 0 : i.getDepositAmount().doubleValue();
            tot += i.getTotal().doubleValue();
        }
        row(sheet, "TỔNG", "", "", "", "", sub, disc, "", dep, tot, Invoice.sumRevenue(list), list.size() + " hóa đơn");
    }

    private void writeRevenueByDay(Sheet sheet, List<Invoice> list, ReportPeriod period) {
        row(sheet, "Ngày", "Số hóa đơn", "Doanh thu (gồm cọc)");
        Map<LocalDate, List<Invoice>> byDay = list.stream().collect(Collectors.groupingBy(ReportExporter::localDay));
        // Không kéo tới tương lai: tháng / năm hiện tại chỉ liệt kê tới hôm nay
        LocalDate last = period.end.isBefore(Times.today().plusDays(1)) ? period.end : Times.today().plusDays(1);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (LocalDate d = period.start; d.isBefore(last); d = d.plusDays(1)) {
            List<Invoice> rows = byDay.getOrDefault(d, List.of());
            row(sheet, d.format(f), rows.size(), Invoice.sumRevenue(rows));
        }
        row(sheet, "TỔNG", list.size(), Invoice.sumRevenue(list));
    }

    private void writeRevenueByMonth(Sheet sheet, List<Invoice> list, ReportPeriod period) {
        row(sheet, "Tháng", "Số hóa đơn", "Doanh thu (gồm cọc)");
        Map<YearMonth, List<Invoice>> byMonth = list.stream().collect(Collectors.groupingBy(i -> YearMonth.from(localDay(i))));
        for (YearMonth m = YearMonth.from(period.start); m.isBefore(YearMonth.from(period.end)); m = m.plusMonths(1)) {
            List<Invoice> rows = byMonth.getOrDefault(m, List.of());
            row(sheet, m.format(DateTimeFormatter.ofPattern("MM/yyyy")), rows.size(), Invoice.sumRevenue(rows));
        }
        row(sheet, "TỔNG", list.size(), Invoice.sumRevenue(list));
    }

    private void writeTopFoods(Sheet sheet, ReportPeriod period) {
        row(sheet, "Hạng", "Món", "Số lượng bán", "Doanh thu món (chưa trừ khuyến mãi)");
        List<Map<String, Object>> foods = topFoods(orderItems.findPaidBetween(InvoiceStatus.PAID, period.startForDb(), period.endForDb()));
        int rank = 1;
        for (Map<String, Object> f : foods) row(sheet, rank++, f.get("food_name"), f.get("sold_quantity"), f.get("revenue"));
    }

    /** Gom theo tên món, sắp theo số lượng giảm dần: [{food_name, sold_quantity, revenue}]. */
    public static List<Map<String, Object>> topFoods(List<OrderItem> items) {
        Map<String, int[]> qty = new LinkedHashMap<>();
        Map<String, double[]> rev = new LinkedHashMap<>();
        for (OrderItem i : items) {
            qty.computeIfAbsent(i.getFoodName(), k -> new int[1])[0] += i.getQuantity();
            rev.computeIfAbsent(i.getFoodName(), k -> new double[1])[0] += i.lineTotal();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        qty.forEach((name, q) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("food_name", name);
            m.put("sold_quantity", q[0]);
            m.put("revenue", rev.get(name)[0]);
            out.add(m);
        });
        out.sort((a, b) -> Integer.compare((int) b.get("sold_quantity"), (int) a.get("sold_quantity")));
        return out;
    }
}
