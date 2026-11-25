package com.dineflow.common;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Xử lý giờ: database lưu UTC, "một ngày" của quán tính theo restaurant.timezone. */
public final class Times {
    public static final ZoneId UTC = ZoneOffset.UTC;
    /** Giống Carbon::toIso8601String(): 2026-11-25T02:00:00+00:00 */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    private static ZoneId restaurantZone = ZoneId.of("Asia/Ho_Chi_Minh");

    private Times() {
    }

    public static void setRestaurantZone(ZoneId zone) {
        restaurantZone = zone;
    }

    public static ZoneId tz() {
        return restaurantZone;
    }

    public static String iso(Instant instant) {
        return instant == null ? null : ISO.format(instant.atZone(UTC));
    }

    public static String iso(ZonedDateTime t) {
        return t == null ? null : ISO.format(t);
    }

    /** Hôm nay theo giờ của quán. */
    public static LocalDate today() {
        return LocalDate.now(restaurantZone);
    }

    /** 00:00 của ngày (giờ quán) đổi ra Instant để so với DB. */
    public static Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(restaurantZone).toInstant();
    }

    public static ZonedDateTime local(Instant instant) {
        return instant.atZone(restaurantZone);
    }

    /**
     * Đọc chuỗi ngày giờ như Carbon::parse(): có múi giờ (Z, +07:00) thì dùng múi đó,
     * không có thì hiểu là giờ UTC (APP_TIMEZONE=UTC). Trả null nếu sai định dạng.
     */
    public static Instant parse(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().replace(' ', 'T');
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(v).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(v).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(v).atStartOfDay(UTC).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    /** Ngày Y-m-d chặt chẽ (date_format:Y-m-d). */
    public static LocalDate parseDate(String value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Định dạng giờ quán, vd "H:mm dd/MM/yyyy". */
    public static String format(Instant instant, String pattern) {
        return DateTimeFormatter.ofPattern(pattern).format(local(instant));
    }

    /** number_format($n, 0, ',', '.') */
    public static String money(double amount) {
        java.text.DecimalFormatSymbols s = new java.text.DecimalFormatSymbols();
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        return new java.text.DecimalFormat("#,##0", s).format(Math.round(amount));
    }
}
