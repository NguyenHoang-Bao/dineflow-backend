package com.dineflow.controller;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Input;
import com.dineflow.entity.DiningTable;
import com.dineflow.entity.Order;
import com.dineflow.entity.OrderItem;
import com.dineflow.enums.OrderStatus;
import com.dineflow.enums.TableStatus;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.DiningTableRepository;
import com.dineflow.repository.OrderRepository;
import com.dineflow.security.Roles;
import com.dineflow.service.OrderService;
import com.dineflow.service.ReservationService;
import com.dineflow.service.Resources;
import com.dineflow.service.TableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

/**
 * Thao tác bàn (NV Order + Admin).
 * CHUYỂN BÀN: đơn đang mở của bàn A → bàn B (B trống, không khóa). A trống lại.
 * GỘP BÀN: món của bàn A thêm vào đơn của bàn B; đơn A bị hủy, A trống lại. B chưa có đơn → như chuyển bàn.
 */
@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
@Roles({UserRole.ORDER_STAFF, UserRole.ADMIN})
public class TableOperationController {
    private final DiningTableRepository tables;
    private final OrderRepository orders;
    private final TableService tableService;
    private final OrderService orderService;
    private final ReservationService reservationService;
    private final Resources res;
    private final TransactionTemplate tx;

    private Long targetId(Map<String, Object> body, String key, Long sourceId) {
        Input in = Input.of(body);
        in.field(key).required("Vui lòng chọn bàn đích.").integer()
                .check(v -> tables.existsById(Input.toLong(v)), "Bàn không tồn tại.")
                .check(v -> !Objects.equals(Input.toLong(v), sourceId), "Bàn đích phải khác bàn hiện tại.");
        in.validate();
        return in.lng(key);
    }

    /** POST /api/tables/{table}/transfer  Body: { to_table_id } */
    @PostMapping("/{id}/transfer")
    public ResponseEntity<Map<String, Object>> transfer(@PathVariable Long id, @Body Map<String, Object> body) {
        DiningTable table = tables.findById(id).orElseThrow(ApiException::notFound);
        Long toId = targetId(body, "to_table_id", id);

        if (table.getStatus() != TableStatus.SERVING || table.getCurrentOrderId() == null) {
            return error("Bàn nguồn phải đang có đơn hàng đang phục vụ.", 422, null);
        }
        DiningTable target = tables.findById(toId).orElseThrow(ApiException::notFound);
        if (target.getStatus() != TableStatus.EMPTY) return error("Bàn " + target.getNumber() + " không trống. Vui lòng chọn bàn khác.", 422, null);
        if (!target.getIsActive()) return error("Bàn " + target.getNumber() + " đã bị vô hiệu hóa.", 422, null);
        if (target.getIsLocked()) return error("Bàn " + target.getNumber() + " đang bị khóa.", 422, null);

        // Bàn đích đang giữ cho khách đặt trước thì không chuyển khách khác sang.
        reservationService.expireStale();
        String reason = tableService.walkInBlockReason(target);
        if (reason != null) return error(reason, 422, null);

        Order order = orders.findById(table.getCurrentOrderId()).orElseThrow(ApiException::notFound);
        tx.executeWithoutResult(s -> {
            order.setTable(target);
            orders.save(order);
            tableService.release(table);
            tableService.occupy(target, order);
        });
        return ok(res.order(order), "Đã chuyển sang bàn " + target.getNumber() + " thành công");
    }

    /** POST /api/tables/{table}/merge  Body: { target_table_id } */
    @PostMapping("/{id}/merge")
    public ResponseEntity<Map<String, Object>> merge(@PathVariable Long id, @Body Map<String, Object> body) {
        DiningTable table = tables.findById(id).orElseThrow(ApiException::notFound);
        Long toId = targetId(body, "target_table_id", id);

        if (table.getStatus() != TableStatus.SERVING || table.getCurrentOrderId() == null) {
            return error("Bàn nguồn phải đang có đơn hàng đang phục vụ.", 422, null);
        }
        DiningTable target = tables.findById(toId).orElseThrow(ApiException::notFound);
        if (!target.getIsActive()) return error("Bàn " + target.getNumber() + " đã bị vô hiệu hóa.", 422, null);
        if (target.getIsLocked()) return error("Bàn " + target.getNumber() + " đang bị khóa.", 422, null);

        boolean targetFree = target.getStatus() == TableStatus.EMPTY || target.getCurrentOrderId() == null;
        // Gộp sang bàn trống = chuyển bàn → bàn đích không được đang giữ cho khách đặt trước.
        if (targetFree) {
            reservationService.expireStale();
            String reason = tableService.walkInBlockReason(target);
            if (reason != null) return error(reason, 422, null);
        }

        Order source = orders.findById(table.getCurrentOrderId()).orElseThrow(ApiException::notFound);
        tx.executeWithoutResult(s -> {
            if (targetFree) {
                // Bàn đích trống → chuyển bàn đơn giản
                source.setTable(target);
                orders.save(source);
                tableService.release(table);
                tableService.occupy(target, source);
                return;
            }
            Order targetOrder = orders.findById(target.getCurrentOrderId()).orElseThrow(ApiException::notFound);
            for (OrderItem item : source.getItems()) {
                // Cùng món + cùng giá + cùng ghi chú → cộng dồn số lượng
                OrderItem existing = targetOrder.getItems().stream()
                        .filter(i -> Objects.equals(i.getFoodId(), item.getFoodId())
                                && i.getPrice().compareTo(item.getPrice()) == 0
                                && Objects.equals(i.getNote(), item.getNote()))
                        .findFirst().orElse(null);
                if (existing != null) {
                    existing.setQuantity(existing.getQuantity() + item.getQuantity());
                } else {
                    OrderItem copy = new OrderItem();
                    copy.setFoodId(item.getFoodId());
                    copy.setFoodName(item.getFoodName());
                    copy.setQuantity(item.getQuantity());
                    copy.setPrice(item.getPrice());
                    copy.setOptions(item.getOptions() == null ? new ArrayList<>() : new ArrayList<>(item.getOptions()));
                    copy.setNote(item.getNote());
                    targetOrder.addItem(copy);
                }
            }
            // Đơn nguồn là đơn nhận bàn đặt trước → chuyển liên kết lượt đặt sang đơn đích (để trừ cọc)
            if (source.getReservation() != null && targetOrder.getReservation() == null) {
                targetOrder.setReservation(source.getReservation());
            }
            targetOrder.recalculateTotal();
            orders.save(targetOrder);

            // Hủy đơn nguồn và giải phóng mọi bàn của nó (kể cả bàn ghép)
            source.setStatus(OrderStatus.CANCELLED);
            orders.save(source);
            orderService.releaseTableIfDone(source);
        });
        return ok(null, "Đã gộp bàn " + table.getNumber() + " vào bàn " + target.getNumber() + " thành công");
    }
}
