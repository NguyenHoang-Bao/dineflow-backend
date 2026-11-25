package com.dineflow.controller;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Db;
import com.dineflow.common.Input;
import com.dineflow.common.Times;
import com.dineflow.entity.DiningTable;
import com.dineflow.entity.Order;
import com.dineflow.entity.OrderItem;
import com.dineflow.entity.User;
import com.dineflow.enums.OrderStatus;
import com.dineflow.enums.PaymentStatus;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.DiningTableRepository;
import com.dineflow.repository.OrderRepository;
import com.dineflow.repository.UserRepository;
import com.dineflow.security.CurrentUser;
import com.dineflow.security.Roles;
import com.dineflow.service.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderRepository orders;
    private final DiningTableRepository tables;
    private final UserRepository users;
    private final OrderService orderService;
    private final TableService tableService;
    private final ReservationService reservationService;
    private final LineItemBuilder builder;
    private final Resources res;
    private final TransactionTemplate tx;
    private final Db db;

    /**
     * GET /api/orders?status=placed,preparing&payment_status=&table_id=&date=2025-01-31&open=1&per_page=&page=
     * Nhân viên / thu ngân / admin thấy tất cả; khách hàng chỉ thấy đơn của mình.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> index(@CurrentUser User user, @RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("payment_status").enumOf(PaymentStatus.class);
        in.field("table_id").integer();
        in.field("date").date();
        in.field("open").bool();
        in.field("per_page").intBetween(1, 100);
        in.validate();

        List<OrderStatus> statuses = new ArrayList<>();
        if (in.filled("status")) for (String s : in.str("status").split(",")) {
            OrderStatus st = OrderStatus.of(s.trim());
            if (st != null) statuses.add(st);
        }

        Specification<Order> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (user.getRole() == UserRole.CUSTOMER) p.add(cb.equal(root.get("customer").get("id"), user.getId()));
            if (!statuses.isEmpty()) p.add(root.get("status").in(statuses));
            if (in.filled("payment_status")) p.add(cb.equal(root.get("paymentStatus"), PaymentStatus.of(in.str("payment_status"))));
            if (in.filled("table_id")) p.add(cb.equal(root.get("table").get("id"), in.lng("table_id")));
            if (in.filled("date")) {
                LocalDate d = in.instant("date").atZone(Times.UTC).toLocalDate(); // whereDate theo giờ DB (UTC)
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), d.atStartOfDay(Times.UTC).toInstant()));
                p.add(cb.lessThan(root.get("createdAt"), d.plusDays(1).atStartOfDay(Times.UTC).toInstant()));
            }
            if (in.bool("open")) p.add(root.get("status").in(OrderStatus.PLACED, OrderStatus.PREPARING, OrderStatus.SERVING));
            return cb.and(p.toArray(Predicate[]::new));
        };
        int perPage = in.filled("per_page") ? in.integer("per_page") : 50;
        int page = Math.max(1, Optional.ofNullable(Input.toLong(query.get("page"))).orElse(1L).intValue());
        Page<Order> result = orders.findAll(spec, PageRequest.of(page - 1, perPage,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));

        return ok(result.getContent().stream().map(res::order).toList(), "Thành công", 200, pageMeta(result, page, perPage));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> show(@CurrentUser User user, @PathVariable Long id) {
        Order order = orders.findById(id).orElseThrow(ApiException::notFound);
        if (user.getRole() == UserRole.CUSTOMER && !Objects.equals(order.getCustomerId(), user.getId())) {
            return error("Bạn không có quyền xem đơn này.", 403, null);
        }
        return ok(res.order(order));
    }

    /**
     * POST /api/orders
     * Nhân viên: bắt buộc có table_id. Bàn đang có đơn mở thì món mới được THÊM vào đơn đó
     * và đơn quay về "placed" nếu đã ra món. Khách hàng: tạo đơn không gắn bàn.
     */
    @Roles({UserRole.ORDER_STAFF, UserRole.ADMIN, UserRole.CUSTOMER})
    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@CurrentUser User user, @Body Map<String, Object> body) {
        boolean isCustomer = user.getRole() == UserRole.CUSTOMER;
        Input in = Input.of(body);
        in.field("table_id").integer().check(v -> tables.existsById(Input.toLong(v)), "Bàn không tồn tại.");
        in.field("customer_id").integer().check(v -> users.existsById(Input.toLong(v)), "Khách hàng không tồn tại.");
        in.field("note").string().max(500);
        builder.validate(in, "items", true);
        in.validate();

        if (!isCustomer && !in.filled("table_id")) {
            throw ApiException.validation("table_id", "Vui lòng chọn bàn cho đơn hàng.");
        }
        List<Map<String, Object>> lines = builder.build(in.list("items"), "items");
        Long tableId = isCustomer ? null : in.lng("table_id");

        // Lượt đặt khách trễ quá 15 phút đã tự hủy → không còn chặn bàn bên dưới.
        if (tableId != null) reservationService.expireStale();

        boolean[] appended = {false};
        Order order = tx.execute(status -> {
            DiningTable table = tableId == null ? null : db.lock(tables.findById(tableId).orElseThrow(ApiException::notFound));
            Order o = null;

            // Bàn đang có đơn mở chưa thanh toán -> gọi thêm vào đơn đó
            if (table != null && table.getCurrentOrderId() != null) {
                Order existing = orders.findById(table.getCurrentOrderId()).map(db::lock).orElse(null);
                if (existing != null && existing.getStatus().isOpen() && !existing.isPaid()) {
                    o = existing;
                    appended[0] = true;
                }
            }

            // Mở bàn mới cho khách vãng lai: không được lấy bàn đang giữ cho khách đặt trước.
            if (o == null && table != null) {
                String reason = tableService.walkInBlockReason(table);
                if (reason != null) throw ApiException.validation("table_id", reason);
            }

            if (o == null) {
                o = new Order();
                o.setTable(table);
                o.setCustomer(isCustomer ? user : (in.filled("customer_id") ? users.getReferenceById(in.lng("customer_id")) : null));
                o.setStaff(isCustomer ? null : user);
                o.setStatus(OrderStatus.PLACED);
                o.setPaymentStatus(PaymentStatus.UNPAID);
                o.setNote(in.str("note"));
            }
            for (Map<String, Object> line : lines) o.addItem(toItem(line));

            if (appended[0]) {
                if (o.getStatus() == OrderStatus.SERVING) o.setStatus(OrderStatus.PLACED);
                if (in.filled("note")) o.setNote(((o.getNote() == null ? "" : o.getNote()) + "\n" + in.str("note")).trim());
            }
            o.recalculateTotal();
            o = orderService.create(o);

            if (table != null && !appended[0]) tableService.occupy(table, o);
            return o;
        });

        return ok(res.order(order),
                appended[0] ? "Đã thêm món vào đơn hiện tại của bàn." : "Tạo đơn hàng thành công.",
                appended[0] ? 200 : 201);
    }

    /** PATCH /api/orders/{id}/status  body: { "status": "preparing" } */
    @Roles({UserRole.ORDER_STAFF, UserRole.CASHIER, UserRole.ADMIN})
    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id, @Body Map<String, Object> body) {
        Order order = orders.findById(id).orElseThrow(ApiException::notFound);
        Input in = Input.of(body);
        in.field("status").required("Vui lòng chọn trạng thái.").enumOf(OrderStatus.class, "Trạng thái không hợp lệ.");
        in.validate();
        OrderStatus to = OrderStatus.of(in.str("status"));

        if (!order.getStatus().canTransitionTo(to)) {
            return error("Không thể chuyển đơn từ \"" + order.getStatus().label() + "\" sang \"" + to.label() + "\".", 422, null);
        }
        // Hủy đơn phải qua PATCH /orders/{id}/cancel (Thu ngân / Admin, bắt buộc lý do)
        if (to == OrderStatus.CANCELLED) {
            return error("Hủy đơn dùng chức năng \"Hủy đơn\" của Thu ngân / Admin và phải chọn lý do.", 422, null);
        }
        String reason = orderService.statusChangeBlockedReason(order, to);
        if (reason != null) return error(reason, 422, null);

        tx.executeWithoutResult(s -> {
            order.setStatus(to);
            orders.save(order);
            orderService.releaseTableIfDone(order);
        });
        return ok(res.order(order), "Đơn " + order.getCode() + ": " + to.label() + ".");
    }

    /** Dòng món đã dựng bằng LineItemBuilder → OrderItem. */
    @SuppressWarnings("unchecked")
    static OrderItem toItem(Map<String, Object> line) {
        OrderItem item = new OrderItem();
        item.setFoodId(((Number) line.get("food_id")).longValue());
        item.setFoodName((String) line.get("food_name"));
        item.setQuantity(((Number) line.get("quantity")).intValue());
        item.setPrice(BigDecimal.valueOf(((Number) line.get("price")).doubleValue()));
        item.setOptions(new ArrayList<>((List<Map<String, Object>>) line.get("options")));
        item.setNote(line.get("note") == null ? null : line.get("note").toString());
        return item;
    }

    /** meta phân trang giống paginate() của Laravel. */
    static Map<String, Object> pageMeta(Page<?> page, int current, int perPage) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("current_page", current);
        meta.put("last_page", Math.max(1, page.getTotalPages()));
        meta.put("per_page", perPage);
        meta.put("total", page.getTotalElements());
        return meta;
    }

}
