package com.dineflow.controller;

import com.dineflow.common.Body;
import com.dineflow.common.Input;
import com.dineflow.common.Times;
import com.dineflow.enums.InvoiceStatus;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.InvoiceRepository;
import com.dineflow.security.Public;
import com.dineflow.security.Roles;
import com.dineflow.security.SignedUrlService;
import com.dineflow.service.ReportExporter;
import com.dineflow.service.ReportPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.dineflow.common.ApiResponse.ok;

/**
 * Xuất báo cáo Excel cho app Admin.
 *   GET  /api/reports/export-periods   (Admin) → các tháng / năm có doanh thu
 *   POST /api/reports/export-link      (Admin) { type: day|month|year, value } → link tải 5 phút
 *   GET  /api/reports/export/download  (link có chữ ký, không cần token) → file .xlsx
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportExportController {
    private static final int LINK_MINUTES = 5;
    private static final String DOWNLOAD_PATH = "/api/reports/export/download";

    private final InvoiceRepository invoices;
    private final ReportExporter exporter;
    private final SignedUrlService signer;

    private List<Instant> paidMoments() {
        return invoices.findPaidMoments(InvoiceStatus.PAID);
    }

    private static List<Map<String, String>> toList(Map<String, String> map) {
        List<Map<String, String>> out = new ArrayList<>();
        map.forEach((value, label) -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("value", value);
            m.put("label", label);
            out.add(m);
        });
        return out;
    }

    @Roles(UserRole.ADMIN)
    @GetMapping("/export-periods")
    public ResponseEntity<Map<String, Object>> periods() {
        List<Instant> moments = paidMoments();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("months", toList(ReportPeriod.availableMonths(moments)));
        data.put("years", toList(ReportPeriod.availableYears(moments)));
        data.put("today", Times.today().toString());
        return ok(data);
    }

    @Roles(UserRole.ADMIN)
    @PostMapping("/export-link")
    public ResponseEntity<Map<String, Object>> link(@Body Map<String, Object> body) {
        Input in = Input.of(body);
        in.field("type").required("Vui lòng chọn kỳ báo cáo.").in(ReportPeriod.TYPES.keySet(), "Kỳ báo cáo phải là ngày, tháng hoặc năm.");
        in.field("value").required("Vui lòng chọn thời gian.").string().max(10);
        in.validate();

        ReportPeriod period = ReportPeriod.make(in.str("type"), in.str("value"), paidMoments()); // kiểm tra trước khi phát link
        Map<String, String> params = new LinkedHashMap<>();
        params.put("type", period.type);
        params.put("value", period.value);
        String url = signer.sign(DOWNLOAD_PATH, params, Instant.now().plus(LINK_MINUTES, ChronoUnit.MINUTES));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url);
        data.put("file_name", period.fileName());
        data.put("label", period.label);
        data.put("expires_in", LINK_MINUTES * 60);
        return ok(data, "Đã tạo link tải báo cáo " + period.label + ".");
    }

    @Public
    @GetMapping("/export/download")
    public ResponseEntity<byte[]> download(@RequestParam Map<String, String> query) throws Exception {
        signer.verify(DOWNLOAD_PATH, query);
        ReportPeriod period = ReportPeriod.make(query.get("type"), query.get("value"), paidMoments());
        byte[] file = exporter.export(period);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(period.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }
}
