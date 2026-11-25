package com.dineflow.repository;

import com.dineflow.entity.OrderItem;
import com.dineflow.enums.InvoiceStatus;
import com.dineflow.enums.OrderStatus;
import com.dineflow.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    /** Món nằm trong đơn chưa thanh toán và chưa hủy. */
    @Query("select case when count(i) > 0 then true else false end from OrderItem i where i.foodId = :foodId and i.order.paymentStatus <> :paid and i.order.status <> :cancelled")
    boolean existsInOpenOrder(@Param("foodId") Long foodId, @Param("paid") PaymentStatus paid, @Param("cancelled") OrderStatus cancelled);

    /** Các dòng món thuộc đơn đã có hóa đơn thanh toán trong [from, to). */
    @Query("select i from OrderItem i, Invoice inv where inv.order = i.order and inv.status = :paidStatus"
            + " and inv.paidAt >= :from and inv.paidAt < :to")
    List<OrderItem> findPaidBetween(@Param("paidStatus") InvoiceStatus paidStatus, @Param("from") Instant from, @Param("to") Instant to);
}
