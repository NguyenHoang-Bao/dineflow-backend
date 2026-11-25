package com.dineflow.service;

import com.dineflow.common.ApiException;
import com.dineflow.entity.DiningTable;
import com.dineflow.entity.Order;
import com.dineflow.entity.User;
import com.dineflow.enums.CancelReason;
import com.dineflow.enums.OrderStatus;
import com.dineflow.repository.DiningTableRepository;
import com.dineflow.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/** Các hàm nghiệp vụ của đơn hàng (Model Order bên PHP). */
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orders;
    private final DiningTableRepository tables;
    private final TableService tableService;

    /** Mã đơn dạng OD241008-4821. */
    public String generateCode() {
        String code;
        do {
            code = "OD" + DateTimeFormatter.ofPattern("yyMMdd").format(Instant.now().atZone(ZoneOffset.UTC))
                    + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
        } while (orders.existsByCode(code));
        return code;
    }

    /** Lưu đơn mới (tự sinh mã nếu chưa có). */
    public Order create(Order o) {
        if (o.getCode() == null) o.setCode(generateCode());
        return orders.save(o);
    }

    /**
     * Đơn chưa có món thì chưa được gửi bếp / ra món / hoàn thành. null nếu được phép.
     */
    public String statusChangeBlockedReason(Order o, OrderStatus to) {
        boolean kitchenStep = to == OrderStatus.PREPARING || to == OrderStatus.SERVING || to == OrderStatus.COMPLETED;
        if (kitchenStep && o.getItems().isEmpty()) {
            return "Đơn " + o.getCode() + " chưa có món nào. Hãy thêm món trước khi chuyển sang \"" + to.label() + "\".";
        }
        return null;
    }

    /**
     * Hủy đơn kèm lý do. Chỉ hủy được đơn chưa thanh toán, chưa hoàn thành, chưa hủy.
     * Lưu lý do, ghi chú, người hủy, thời điểm; trả mọi bàn của đơn về trống.
     */
    @Transactional
    public void cancelBy(Order o, User by, CancelReason reason, String note) {
        note = note != null && !note.isBlank() ? note.trim() : null;
        if (o.isPaid()) throw ApiException.validation("order", "Đơn đã thanh toán không thể hủy.");
        if (o.getStatus() == OrderStatus.CANCELLED) throw ApiException.validation("order", "Đơn này đã được hủy trước đó.");
        if (o.getStatus() == OrderStatus.COMPLETED) throw ApiException.validation("order", "Đơn đã hoàn thành không thể hủy.");
        if (reason == CancelReason.OTHER && note == null)
            throw ApiException.validation("note", "Chọn lý do \"Khác\" thì phải ghi rõ lý do.");

        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelReason(reason);
        o.setCancelNote(note);
        o.setCancelledBy(by);
        o.setCancelledAt(Instant.now());
        orders.save(o);
        releaseTableIfDone(o);
    }

    /**
     * Trả MỌI bàn đang gắn với đơn (current_order_id = id đơn) về trống khi đơn đã kết thúc:
     * bị hủy, hoặc hoàn thành và đã thanh toán.
     */
    public void releaseTableIfDone(Order o) {
        boolean finished = o.getStatus() == OrderStatus.CANCELLED
                || (o.getStatus() == OrderStatus.COMPLETED && o.isPaid());
        if (!finished) return;
        for (DiningTable t : tables.findByCurrentOrderId(o.getId())) tableService.release(t);
    }
}
