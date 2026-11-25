package com.dineflow.service;

import com.dineflow.config.LoyaltyConfig;

/** Quy tắc thành viên Kiểu A, đọc từ LoyaltyConfig. */
public final class LoyaltyRules {
    private LoyaltyRules() {
    }

    /** Tên hạng (silver / gold / platinum) ứng với số điểm tích lũy. */
    public static String tierFor(int points) {
        String tier = "silver";
        for (var e : LoyaltyConfig.TIERS.entrySet()) {
            if (points >= e.getValue().minPoints()) tier = e.getKey();
        }
        return tier;
    }

    /** % giảm trên hóa đơn của hạng ứng với số điểm. */
    public static int discountPercentFor(int points) {
        return LoyaltyConfig.TIERS.get(tierFor(points)).discountPercent();
    }

    /** Điểm được cộng cho số tiền khách thực trả, làm tròn xuống theo từng mức. Vd 95.000đ → 18 điểm. */
    public static int pointsEarned(double paidAmount) {
        if (paidAmount <= 0) return 0;
        return (int) Math.floor(paidAmount / LoyaltyConfig.AMOUNT_PER_STEP) * LoyaltyConfig.POINTS_PER_STEP;
    }
}
