package com.dineflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "foods")
@Getter
@Setter
public class Food extends BaseEntity {
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(columnDefinition = "text")
    private String description;

    /** Path trong thư mục storage (vd foods/bac_xiu.jpg) hoặc URL http(s). */
    private String image;

    @Column(length = 50)
    private String icon;

    @Column(length = 9)
    private String color;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating = new BigDecimal("4.5");

    @Column(name = "is_best_seller", nullable = false)
    private Boolean isBestSeller = false;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @OneToMany(mappedBy = "food", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<FoodOption> options = new ArrayList<>();
}
