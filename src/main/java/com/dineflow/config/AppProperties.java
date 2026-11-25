package com.dineflow.config;

import com.dineflow.common.Times;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/** Đọc cấu hình trong application.properties (thay cho config/restaurant.php + APP_URL/APP_KEY). */
@Getter
@Component
public class AppProperties {
    @Value("${app.url}")
    private String url;
    @Value("${app.key}")
    private String key;
    @Value("${app.seed:true}")
    private boolean seed;

    @Value("${restaurant.name}")
    private String restaurantName;
    @Value("${restaurant.timezone}")
    private String restaurantTimezone;
    @Value("${restaurant.bank.code}")
    private String bankCode;
    @Value("${restaurant.bank.name}")
    private String bankName;
    @Value("${restaurant.bank.account-no}")
    private String bankAccountNo;
    @Value("${restaurant.bank.account-name}")
    private String bankAccountName;

    @Value("${app.mail.mode:log}")
    private String mailMode;
    @Value("${app.mail.from}")
    private String mailFrom;
    @Value("${app.mail.from-name}")
    private String mailFromName;

    @PostConstruct
    void init() {
        Times.setRestaurantZone(ZoneId.of(restaurantTimezone));
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
    }

    /** Giống config('restaurant') trả về cho app qua GET /api/settings. */
    public Map<String, Object> restaurantConfig() {
        Map<String, Object> bank = new LinkedHashMap<>();
        bank.put("code", bankCode);
        bank.put("name", bankName);
        bank.put("account_no", bankAccountNo);
        bank.put("account_name", bankAccountName);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", restaurantName);
        m.put("timezone", restaurantTimezone);
        m.put("bank", bank);
        return m;
    }

    /** URL tuyệt đối của file trong thư mục storage công khai (Storage::disk('public')->url()). */
    public String storageUrl(String path) {
        return url + "/storage/" + path;
    }
}
