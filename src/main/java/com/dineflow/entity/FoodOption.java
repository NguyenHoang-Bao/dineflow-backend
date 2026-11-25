package com.dineflow.entity;

import com.dineflow.enums.FoodOptionGroup;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "food_options")
@Getter
@Setter
public class FoodOption extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Column(nullable = false)
    private String name;

    /** "group" là từ khóa SQL nên phải đặt trong dấu `. */
    @Column(name = "`group`", nullable = false, length = 30)
    private FoodOptionGroup group = FoodOptionGroup.SIZE;

    @Column(name = "price_modifier", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceModifier = BigDecimal.ZERO;

    /** Snapshot lưu vào order_items.options / reservations.pre_order_items. */
    public Map<String, Object> toSnapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId());
        m.put("group", group.value());
        m.put("name", name);
        m.put("price_modifier", priceModifier.doubleValue());
        return m;
    }
}
