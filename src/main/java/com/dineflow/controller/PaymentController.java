package com.dineflow.controller;

import com.dineflow.dto.payment.SepayWebhookRequest;
import com.dineflow.entity.DiningTable;
import com.dineflow.entity.Order;
import com.dineflow.entity.Reservation;
import com.dineflow.enums.PaymentStatus;
import com.dineflow.enums.ReservationStatus;
import com.dineflow.repository.OrderRepository;
import com.dineflow.repository.ReservationRepository;
import com.dineflow.security.Public;
import com.dineflow.service.MailService;
import com.dineflow.service.NotificationService;
import com.dineflow.service.OrderService;
import com.dineflow.service.Resources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.dineflow.common.ApiResponse.ok;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final OrderRepository orders;
    private final ReservationRepository reservations;
    private final OrderService orderService;
    private final NotificationService notifications;
    private final MailService mailService;
    private final Resources res;

    @Value("${sepay.api-key:}")
    private String sepayApiKey;

    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("(?i)(?:OD[-\\s]?\\d{6}[-\\s]?\\d{4}|OD\\d{6}-\\d{4}|DF-ORD-\\d+|ORD-?\\d+)");
    private static final Pattern RES_CODE_PATTERN = Pattern.compile("(?i)(?:DF[-\\s]?\\d{4,6}|DF-RES-\\d+|RES-?\\d+)");

    /**
     * POST /api/payment/sepay-webhook
     * Webhook nhận thông báo biến động số dư tài khoản ngân hàng từ SePay.
     */
    @Public
    @PostMapping("/sepay-webhook")
    @Transactional
    public ResponseEntity<Map<String, Object>> handleSepayWebhook(
            @RequestBody SepayWebhookRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("[SePay Webhook] Nhận request: gateway={}, amount={}, content={}",
                request.getGateway(), request.getTransferAmount(), request.getContent());

        // 1. Kiểm tra API Key xác thực nếu có cấu hình
        if (sepayApiKey != null && !sepayApiKey.isBlank() && !"SEPAY_API_KEY_SECRET".equals(sepayApiKey)) {
            String expectedAuth = "Apikey " + sepayApiKey;
            if (authHeader == null || !authHeader.trim().equalsIgnoreCase(expectedAuth.trim())) {
                log.warn("[SePay Webhook] Từ chối: Sai Authorization Header.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "success", false,
                        "message", "Unauthorized SePay webhook request"
                ));
            }
        }

        // 2. Chỉ xử lý tiền vào (transferType = "in")
        if (request.getTransferType() != null && !"in".equalsIgnoreCase(request.getTransferType())) {
            log.info("[SePay Webhook] Bỏ qua: Không phải giao dịch tiền vào (transferType = {}).", request.getTransferType());
            return ResponseEntity.ok(Map.of("success", true, "message", "Ignored: Not incoming transfer"));
        }

        String content = request.getContent() == null ? "" : request.getContent().trim();
        BigDecimal amount = request.getTransferAmount() != null ? request.getTransferAmount() : BigDecimal.ZERO;

        boolean processed = false;

        // 3. Phân tích cú pháp: Kiểm tra Đơn hàng (Order)
        Matcher orderMatcher = ORDER_CODE_PATTERN.matcher(content);
        if (orderMatcher.find()) {
            String matchedCode = orderMatcher.group();
            Order order = orders.findByCode(matchedCode).orElse(null);
            if (order == null && matchedCode.toUpperCase().startsWith("OD")) {
                String clean = matchedCode.toUpperCase().replaceAll("[^A-Z0-9]", "");
                if (clean.length() == 12) {
                    String withDash = clean.substring(0, 8) + "-" + clean.substring(8);
                    order = orders.findByCode(withDash).orElse(null);
                }
            }

            if (order != null) {
                if (order.getPaymentStatus() != PaymentStatus.PAID) {
                    order.setPaymentStatus(PaymentStatus.PAID);
                    orders.save(order);
                    orderService.releaseTableIfDone(order);

                    log.info("[SePay Webhook] Xác nhận thanh toán thành công cho Đơn hàng #{}", order.getCode());

                    if (order.getCustomerId() != null && order.getReservation() != null) {
                        notifications.reservationConfirmed(order.getReservation());
                    }
                }
                processed = true;
            }
        }

        // 4. Phân tích cú pháp: Kiểm tra Lượt đặt bàn (Reservation)
        Matcher resMatcher = RES_CODE_PATTERN.matcher(content);
        if (resMatcher.find()) {
            String matchedCode = resMatcher.group().toUpperCase().replaceAll("\\s+", "-");
            Reservation r = reservations.findByCode(matchedCode).orElse(null);
            if (r == null && !matchedCode.contains("-") && matchedCode.startsWith("DF")) {
                r = reservations.findByCode("DF-" + matchedCode.substring(2)).orElse(null);
            }

            if (r != null) {
                r.setDepositAmount(amount);
                r.setDepositPaidAt(Instant.now());
                r.setHoldExpiresAt(null); // Đã cọc thành công, hủy hạn giữ bàn
                r.setStatus(ReservationStatus.UPCOMING); // Đặt trạng thái về Sắp tới
                reservations.save(r);

                log.info("[SePay Webhook] Xác nhận tiền cọc thành công cho Lượt đặt bàn #{}", r.getCode());

                notifications.reservationConfirmed(r);

                // Gửi email xác nhận đặt bàn
                try {
                    List<DiningTable> ts = res.tablesOf(r);
                    List<String> tableNumbers = ts.stream().map(DiningTable::getNumber).toList();
                    mailService.sendReservationConfirmation(r, tableNumbers);
                } catch (Exception e) {
                    log.warn("[SePay Webhook] Không thể gửi email xác nhận đặt bàn: {}", e.getMessage());
                }
                processed = true;
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "processed", processed,
                "message", processed ? "Payment processed successfully" : "No matching order or reservation found"
        ));
    }

    /**
     * GET /api/payment/check-status/{code}
     * Kiểm tra trạng thái thanh toán (để App Flutter polling tự động nhảy màn hình).
     */
    @Public
    @GetMapping("/check-status/{code}")
    public ResponseEntity<Map<String, Object>> checkStatus(@PathVariable String code) {
        // 1. Kiểm tra trong Đơn hàng
        Order order = orders.findByCode(code).orElse(null);
        if (order != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "order");
            m.put("code", order.getCode());
            m.put("is_paid", order.isPaid());
            m.put("status", order.getStatus().value());
            m.put("total", order.getTotalAmount());
            return ok(m);
        }

        // 2. Kiểm tra trong Lượt đặt bàn
        Reservation r = reservations.findByCode(code).orElse(null);
        if (r == null && !code.contains("-") && code.startsWith("DF")) {
            r = reservations.findByCode("DF-" + code.substring(2)).orElse(null);
        }
        if (r != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "reservation");
            m.put("code", r.getCode());
            m.put("is_paid", r.isDepositPaid());
            m.put("status", r.getStatus().value());
            m.put("deposit_amount", r.getDepositAmount());
            return ok(m);
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false,
                "message", "Không tìm thấy đơn hàng hoặc lịch đặt bàn với mã: " + code
        ));
    }
}