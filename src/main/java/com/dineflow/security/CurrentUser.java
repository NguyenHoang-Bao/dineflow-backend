package com.dineflow.security;

import java.lang.annotation.*;

/** Gắn vào tham số User trong controller để lấy người đang đăng nhập ($request->user()). */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
