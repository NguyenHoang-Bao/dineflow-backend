package com.dineflow.security;

import java.lang.annotation.*;

/** Giới hạn số lần gọi mỗi phút theo IP, giống ->middleware('throttle:10,1'). */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Throttle {
    int perMinute() default 10;
}
