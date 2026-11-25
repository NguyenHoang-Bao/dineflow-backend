package com.dineflow.entity;

import com.dineflow.enums.UserRole;
import com.dineflow.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {
    /** Mật khẩu mặc định khi Admin tạo / đặt lại tài khoản nhân viên. */
    public static final String DEFAULT_PASSWORD = "123456";

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 20, unique = true)
    private String phone;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** Bản băm BCrypt. */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.CUSTOMER;

    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "member_code", length = 20, unique = true)
    private String memberCode;

    @Column(nullable = false)
    private Integer points = 0;

    @Column(name = "remember_token", length = 100)
    private String rememberToken;

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasRole(UserRole... roles) {
        for (UserRole r : roles) if (r == role) return true;
        return false;
    }
}
