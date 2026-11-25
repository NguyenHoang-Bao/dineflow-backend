package com.dineflow.controller;

import com.dineflow.config.AppProperties;
import com.dineflow.config.LoyaltyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.dineflow.common.ApiResponse.ok;

/** GET /api/settings — tài khoản nhận chuyển khoản (VietQR) và quy tắc thành viên. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettingsController {
    private final AppProperties props;

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> show() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("restaurant", props.restaurantConfig());
        data.put("loyalty", LoyaltyConfig.asMap());
        return ok(data);
    }
}
