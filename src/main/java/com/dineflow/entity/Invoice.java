package com.dineflow.entity;

import com.dineflow.enums.InvoiceStatus;
import com.dineflow.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private User cashier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /** Tiền cọc được khấu trừ trong hóa đơn này. */
    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Column(name = "paid_at")
    private Instant paidAt;

    /**
     * Doanh thu của hóa đơn = thực thu tại quầy (total) + tiền cọc khách đã trả lúc đặt bàn.
     * Báo cáo CA thì vẫn dùng total vì tiền cọc không đi qua ngăn kéo tiền của thu ngân.
     */
    public double revenueAmount() {
        return total.doubleValue() + (depositAmount == null ? 0 : depositAmount.doubleValue());
    }

    public static double sumRevenue(Iterable<Invoice> invoices) {
        double s = 0;
        for (Invoice i : invoices) s += i.revenueAmount();
        return s;
    }

    public Long getShiftId() {
        return shift == null ? null : shift.getId();
    }

    public Long getCashierId() {
        return cashier == null ? null : cashier.getId();
    }
}
