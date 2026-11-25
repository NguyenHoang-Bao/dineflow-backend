package com.dineflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Token đăng nhập, cùng cấu trúc bảng personal_access_tokens của Laravel Sanctum.
 * Token gửi cho app có dạng "{id}|{chuỗi ngẫu nhiên}", DB chỉ lưu SHA-256 của chuỗi ngẫu nhiên.
 */
@Entity
@Table(name = "personal_access_tokens")
@Getter
@Setter
public class PersonalAccessToken extends BaseEntity {
    public static final String USER_TYPE = "App\\Models\\User";

    @Column(name = "tokenable_type", nullable = false)
    private String tokenableType = USER_TYPE;

    @Column(name = "tokenable_id", nullable = false)
    private Long tokenableId;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(nullable = false, length = 64, unique = true)
    private String token;

    @Column(columnDefinition = "text")
    private String abilities = "[\"*\"]";

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
