package com.dineflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** Thông báo in-app cho khách hàng (bảng notifications). */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class AppNotification extends BaseEntity {
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** reservation_confirmed | reservation_cancelled */
    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> data;

    /** null = chưa đọc. */
    @Column(name = "read_at")
    private Instant readAt;
}
