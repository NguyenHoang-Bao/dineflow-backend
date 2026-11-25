package com.dineflow.security;

import com.dineflow.enums.UserRole;

import java.lang.annotation.*;

/** Giới hạn theo vai trò, giống ->middleware('role:cashier,admin'). Đặt trên class hoặc method. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Roles {
    UserRole[] value();
}
