package com.dineflow.common;

import java.lang.annotation.*;

/**
 * Body của request dạng Map, đọc được cả JSON lẫn form-urlencoded / multipart
 * (Laravel nhận cả hai kiểu nên app có thể đang gửi kiểu nào cũng được).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Body {
}
