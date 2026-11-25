package com.dineflow.security;

import java.lang.annotation.*;

/** Route không cần đăng nhập (login, register, quên mật khẩu, tải Excel bằng link ký). */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Public {
}
