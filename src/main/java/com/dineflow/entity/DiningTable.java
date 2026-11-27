package com.dineflow.entity;

import com.dineflow.enums.TableStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Bàn ăn (bảng `tables`). */
@Entity
@Table(name = "tables")
@Getter
@Setter
public class DiningTable extends BaseEntity {
    @Column(nullable = false, unique = true, length = 30)
    private String number;

    @Column(nullable = false, length = 50)
    private String area;

    @Column(nullable = false)
    private Integer capacity = 4;

    /** Tiền cọc giữ bàn (VNĐ), 0 = không yêu cầu cọc */
    @Column(name = "deposit_amount", precision = 12, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private TableStatus status = TableStatus.EMPTY;

    @Column(name = "serving_since")
    private Instant servingSince;

    @Column(name = "current_order_id")
    private Long currentOrderId;

    /** Admin khóa bàn – NV không thể mở, vẫn hiển thị. */
    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked = false;

    /** false = ẩn khỏi app hoàn toàn (bàn bị tháo bỏ). */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public boolean isEmpty() {
        return status == TableStatus.EMPTY;
    }
}
