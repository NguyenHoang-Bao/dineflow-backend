package com.dineflow.controller;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Input;
import com.dineflow.common.Times;
import com.dineflow.entity.Order;
import com.dineflow.entity.User;
import com.dineflow.enums.CancelReason;
import com.dineflow.enums.OrderStatus;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.OrderRepository;
import com.dineflow.security.CurrentUser;
import com.dineflow.security.Roles;
import com.dineflow.service.OrderService;
import com.dineflow.service.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.dineflow.common.ApiResponse.ok;

/** Hủy đơn hàng (Thu ngân + Admin) — bắt buộc lý do. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CancelOrderController {
    private final OrderRepository orders;
    private final OrderService orderService;
    private final Resources res;

    /** PATCH /api/orders/{id}/cancel  body: { "reason": "wrong_entry|customer_left|out_of_stock|other", "note": "..." } */
    @Roles({UserRole.CASHIER, UserRole.ADMIN})
    @PatchMapping("/orders/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@CurrentUser User user, @PathVariable Long id,
                                                      @Body Map<String, Object> body) {
        Order order = orders.findById(id).orElseThrow(ApiException::notFound);
        Input in = Input.of(body);
        in.field("reason").required("Vui lòng chọn lý do hủy đơn.").enumOf(CancelReason.class, "Lý do hủy không hợp lệ.");
        in.field("note").requiredIf("other".equals(in.str("reason")), "Chọn lý do \"Khác\" thì phải ghi rõ lý do.")
                .string().max(255, "Ghi chú tối đa 255 ký tự.");
        in.validate();

        orderService.cancelBy(order, user, CancelReason.of(in.str("reason")), in.str("note"));
        return ok(res.order(order), "Đã hủy đơn #" + order.getCode());
    }

    /**
     * GET /api/admin/cancelled-orders?date=2026-11-13 (Admin)
     * Các đơn bị hủy trong một ngày (giờ quán, mặc định hôm nay), mới nhất trước.
     */
    @Roles(UserRole.ADMIN)
    @GetMapping("/admin/cancelled-orders")
    public ResponseEntity<Map<String, Object>> index(@RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("date").dateFormatYmd();
        in.validate();
        LocalDate day = in.filled("date") ? Times.parseDate(in.str("date")) : Times.today();
        List<Order> list = orders.findCancelledBetween(OrderStatus.CANCELLED, Times.startOfDay(day), Times.startOfDay(day.plusDays(1)));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day.toString());
        data.put("total_amount", list.stream().mapToDouble(o -> o.getTotalAmount().doubleValue()).sum());
        data.put("orders", list.stream().map(res::order).toList());
        return ok(data);
    }
}
