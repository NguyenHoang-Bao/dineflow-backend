package com.dineflow.controller;

import com.dineflow.common.ApiException;
import com.dineflow.common.Times;
import com.dineflow.entity.AppNotification;
import com.dineflow.entity.Favorite;
import com.dineflow.entity.Food;
import com.dineflow.entity.User;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.AppNotificationRepository;
import com.dineflow.repository.FavoriteRepository;
import com.dineflow.repository.FoodRepository;
import com.dineflow.security.CurrentUser;
import com.dineflow.security.Roles;
import com.dineflow.service.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static com.dineflow.common.ApiResponse.ok;

/**
 * Món yêu thích & thông báo in-app của khách hàng
 * (FavoriteController + NotificationController bên PHP).
 */
@RestController
@RequestMapping("/api/customers/me")
@RequiredArgsConstructor
@Roles(UserRole.CUSTOMER)
public class CustomerController {
    private final FavoriteRepository favorites;
    private final FoodRepository foods;
    private final AppNotificationRepository notifications;
    private final Resources res;

    /** GET /customers/me/favorites */
    @GetMapping("/favorites")
    public ResponseEntity<Map<String, Object>> favorites(@CurrentUser User user) {
        return ok(favorites.findByCustomerIdOrderByIdAsc(user.getId()).stream()
                .map(Favorite::getFood).filter(Objects::nonNull).map(res::food).toList());
    }

    /** POST /customers/me/favorites/{food} — thêm nếu chưa có, xóa nếu đã có. */
    @PostMapping("/favorites/{foodId}")
    public ResponseEntity<Map<String, Object>> toggle(@CurrentUser User user, @PathVariable Long foodId) {
        Food food = foods.findById(foodId).orElseThrow(ApiException::notFound);
        var existing = favorites.findByCustomerIdAndFood_Id(user.getId(), food.getId());
        boolean favorited;
        if (existing.isPresent()) {
            favorites.delete(existing.get());
            favorited = false;
        } else {
            Favorite f = new Favorite();
            f.setCustomerId(user.getId());
            f.setFood(food);
            favorites.save(f);
            favorited = true;
        }
        return ok(Map.of("favorited", favorited), favorited ? "Đã thêm vào yêu thích" : "Đã bỏ khỏi yêu thích");
    }

    /** GET /customers/me/notifications — mới nhất trước. */
    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> notifications(@CurrentUser User user) {
        return ok(notifications.findByCustomerIdOrderByCreatedAtDescIdDesc(user.getId()).stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("title", n.getTitle());
            m.put("body", n.getBody());
            m.put("data", n.getData());
            m.put("read", n.getReadAt() != null);
            m.put("created_at", Times.iso(n.getCreatedAt()));
            return m;
        }).toList());
    }

    /** PATCH /customers/me/notifications/{id}/read */
    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@CurrentUser User user, @PathVariable Long id) {
        AppNotification n = notifications.findByIdAndCustomerId(id, user.getId()).orElseThrow(ApiException::notFound);
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            notifications.save(n);
        }
        return ok(null, "Đã đánh dấu đã đọc.");
    }

    /** POST /customers/me/notifications/read-all */
    @Transactional
    @PostMapping("/notifications/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(@CurrentUser User user) {
        notifications.markAllRead(user.getId(), Instant.now());
        return ok(null, "Đã đánh dấu tất cả đã đọc.");
    }
}
