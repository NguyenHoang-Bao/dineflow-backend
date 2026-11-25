package com.dineflow.controller;

import com.dineflow.entity.User;
import com.dineflow.enums.UserRole;
import com.dineflow.enums.UserStatus;
import com.dineflow.repository.UserRepository;
import com.dineflow.security.Roles;
import com.dineflow.service.LoyaltyRules;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

/** GET /api/members/{code} — tra cứu thành viên theo mã thẻ (màn Quét QR của Thu ngân). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MemberController {
    private final UserRepository users;

    @Roles({UserRole.CASHIER, UserRole.ADMIN})
    @GetMapping("/members/{code}")
    public ResponseEntity<Map<String, Object>> show(@PathVariable String code) {
        User c = users.findByMemberCode(code.trim().toUpperCase())
                .filter(u -> u.getRole() == UserRole.CUSTOMER && u.getStatus() == UserStatus.ACTIVE)
                .orElse(null);
        if (c == null) return error("Không tìm thấy thành viên có mã \"" + code + "\".", 404, null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("phone", c.getPhone());
        m.put("member_code", c.getMemberCode());
        m.put("points", c.getPoints());
        m.put("tier", LoyaltyRules.tierFor(c.getPoints()));
        return ok(m);
    }
}
