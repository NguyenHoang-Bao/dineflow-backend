package com.dineflow.entity;

import com.dineflow.enums.ShiftStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shifts")
@Getter
@Setter
public class Shift extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id", nullable = false)
    private User cashier;

    @Column(nullable = false, length = 20)
    private ShiftStatus status = ShiftStatus.OPEN;

    /** Tiền mặt đầu ca. */
    @Column(name = "opening_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingCash = BigDecimal.ZERO;

    /** Tiền mặt đếm được lúc đóng ca. */
    @Column(name = "closing_cash_counted", precision = 12, scale = 2)
    private BigDecimal closingCashCounted;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public boolean isOpen() {
        return status == ShiftStatus.OPEN;
    }

    public Long getCashierId() {
        return cashier == null ? null : cashier.getId();
    }
}
