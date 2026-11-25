package com.dineflow.entity;

import com.dineflow.enums.PaymentMethod;
import com.dineflow.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "reservations")
@Getter
@Setter
public class Reservation extends BaseEntity {
    /** Một bàn đã có lượt đặt thì không nhận lượt khác trong ±90 phút quanh giờ đó. */
    public static final int WINDOW_MINUTES = 90;
    /** Khách trễ quá số phút này so với giờ hẹn thì lượt đặt tự hủy (coi như không đến), mất cọc. */
    public static final int GRACE_MINUTES = 15;
    /** Nhận bàn sớm tối đa số phút này trước giờ hẹn. */
    public static final int EARLY_CHECKIN_MINUTES = 30;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    /** [1, 2] khi ghép bàn (cột JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "table_ids", nullable = false)
    private List<Long> tableIds = new ArrayList<>();

    @Column(nullable = false, length = 50)
    private String area;

    @Column(name = "date_time", nullable = false)
    private Instant dateTime;

    @Column(name = "guest_count", nullable = false)
    private Integer guestCount;

    @Column(columnDefinition = "text")
    private String note;

    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.UPCOMING;

    /** Lượt đặt đang "giữ bàn" chờ khách xác nhận: có hạn. null = đã xác nhận. */
    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    /** [{food_id, food_name, quantity, price, options:[...], note}] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pre_order_items")
    private List<Map<String, Object>> preOrderItems;

    @Column(name = "pre_order_payment", length = 20)
    private PaymentMethod preOrderPayment;

    @Column(name = "deposit_amount", precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "deposit_paid_at")
    private Instant depositPaidAt;

    public Long getCustomerId() {
        return customer == null ? null : customer.getId();
    }

    public List<Long> getTableIds() {
        // JSON có thể đọc ra Integer: chuẩn hóa về Long
        List<Long> out = new ArrayList<>();
        if (tableIds != null) for (Object o : (List<?>) tableIds) out.add(((Number) o).longValue());
        return out;
    }

    /** Tổng tiền món đặt trước (đơn giá + options) * số lượng. */
    public double preOrderTotal() {
        return preOrderTotal(preOrderItems);
    }

    public static double preOrderTotal(List<Map<String, Object>> items) {
        if (items == null) return 0;
        double sum = 0;
        for (Map<String, Object> item : items) {
            double extra = 0;
            Object opts = item.get("options");
            if (opts instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m && m.get("price_modifier") instanceof Number n) extra += n.doubleValue();
                }
            }
            double price = ((Number) item.get("price")).doubleValue();
            int qty = ((Number) item.get("quantity")).intValue();
            sum += (price + extra) * qty;
        }
        return sum;
    }

    /** Tiền cọc = 50% tổng món đặt trước, làm tròn lên 100đ. 0 nếu không có món. */
    public static double calculateDeposit(List<Map<String, Object>> items) {
        double total = preOrderTotal(items);
        if (total <= 0) return 0.0;
        return Math.ceil((total * 0.5) / 100) * 100;
    }

    public boolean isDepositPaid() {
        return depositPaidAt != null;
    }

    public boolean isHeld() {
        return holdExpiresAt != null;
    }

    public Instant checkInOpensAt() {
        return dateTime.minus(EARLY_CHECKIN_MINUTES, ChronoUnit.MINUTES);
    }

    public Instant checkInClosesAt() {
        return dateTime.plus(GRACE_MINUTES, ChronoUnit.MINUTES);
    }

    public BigDecimal depositOrZero() {
        return depositAmount == null ? BigDecimal.ZERO : depositAmount.setScale(2, RoundingMode.HALF_UP);
    }
}
