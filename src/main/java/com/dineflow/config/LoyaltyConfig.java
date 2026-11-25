package com.dineflow.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quy tắc thành viên Kiểu A (config/loyalty.php).
 * - Điểm chỉ dùng để xét hạng, không bị trừ hay đổi thành tiền.
 * - Cứ mỗi AMOUNT_PER_STEP đồng khách THỰC TRẢ thì được POINTS_PER_STEP điểm.
 * - Mỗi hạng được giảm discount_percent % trên hóa đơn; không cộng dồn với voucher (lấy mức cao hơn).
 * Đây là NƠI DUY NHẤT chứa các con số này.
 */
public final class LoyaltyConfig {
    public static final int AMOUNT_PER_STEP = 10000;
    public static final int POINTS_PER_STEP = 2;

    public record Tier(String label, int minPoints, int discountPercent) {
    }

    /** Thứ tự từ thấp đến cao. Tên hạng khớp enum MemberTier bên Flutter. */
    public static final Map<String, Tier> TIERS = new LinkedHashMap<>();

    static {
        TIERS.put("silver", new Tier("Bạc", 0, 0));
        TIERS.put("gold", new Tier("Vàng", 500, 5));
        TIERS.put("platinum", new Tier("Kim cương", 1500, 10));
    }

    private LoyaltyConfig() {
    }

    /** Dạng JSON giống config('loyalty'). */
    public static Map<String, Object> asMap() {
        Map<String, Object> tiers = new LinkedHashMap<>();
        TIERS.forEach((name, t) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", t.label());
            m.put("min_points", t.minPoints());
            m.put("discount_percent", t.discountPercent());
            tiers.put(name, m);
        });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount_per_step", AMOUNT_PER_STEP);
        m.put("points_per_step", POINTS_PER_STEP);
        m.put("tiers", tiers);
        return m;
    }
}
