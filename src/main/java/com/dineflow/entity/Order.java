package com.dineflow.entity;

import com.dineflow.enums.CancelReason;
import com.dineflow.enums.OrderStatus;
import com.dineflow.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order extends BaseEntity {
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private DiningTable table;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private User staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PLACED;

    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "cancel_reason", length = 30)
    private CancelReason cancelReason;

    @Column(name = "cancel_note")
    private String cancelNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    public boolean isPaid() {
        return paymentStatus == PaymentStatus.PAID;
    }

    public Long getTableId() {
        return table == null ? null : table.getId();
    }

    public Long getCustomerId() {
        return customer == null ? null : customer.getId();
    }

    public Long getStaffId() {
        return staff == null ? null : staff.getId();
    }

    public Long getReservationId() {
        return reservation == null ? null : reservation.getId();
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    /** Tính lại tổng tiền từ các món. */
    public void recalculateTotal() {
        double sum = 0;
        for (OrderItem i : items) sum += i.lineTotal();
        totalAmount = BigDecimal.valueOf(sum).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
