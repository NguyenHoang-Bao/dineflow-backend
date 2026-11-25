package com.dineflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "food_id")
    private Long foodId;

    /** Snapshot tên món lúc đặt. */
    @Column(name = "food_name", nullable = false)
    private String foodName;

    @Column(nullable = false)
    private Integer quantity = 1;

    /** [{id, group, name, price_modifier}] */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> options = new ArrayList<>();

    /** Đơn giá gốc lúc đặt (chưa gồm options). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(columnDefinition = "text")
    private String note;

    /** Tổng phụ thu từ options. */
    public double optionsExtra() {
        double sum = 0;
        if (options != null) for (Map<String, Object> o : options) {
            if (o.get("price_modifier") instanceof Number n) sum += n.doubleValue();
        }
        return sum;
    }

    /** Đơn giá đã gồm options. */
    public double unitPrice() {
        return price.doubleValue() + optionsExtra();
    }

    /** Thành tiền dòng. */
    public double lineTotal() {
        return unitPrice() * quantity;
    }
}
