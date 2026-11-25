package com.dineflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "promotions")
@Getter
@Setter
public class Promotion extends BaseEntity {
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent;

    /** null = không giới hạn. */
    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Trạng thái hiển thị, khớp PromotionStatus { active, upcoming, expired } bên Flutter. */
    public String displayStatus() {
        Instant now = Instant.now();
        if (now.isBefore(validFrom)) return "upcoming";
        return (isActive && !now.isAfter(validTo)) ? "active" : "expired";
    }

    /** Voucher có áp dụng được cho đơn có giá trị amount không. */
    public boolean isApplicableTo(double amount) {
        Instant now = Instant.now();
        return Boolean.TRUE.equals(isActive)
                && !now.isBefore(validFrom) && !now.isAfter(validTo)
                && amount >= minOrderAmount.doubleValue();
    }

    /** Số tiền được giảm cho đơn amount (đã áp trần max_discount). */
    public double calculateDiscount(double amount) {
        if (!isApplicableTo(amount)) return 0;
        double discount = Math.round(amount * discountPercent / 100.0);
        return maxDiscount != null ? Math.min(discount, maxDiscount.doubleValue()) : discount;
    }
}
