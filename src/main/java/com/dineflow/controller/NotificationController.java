package com.dineflow.controller;

import com.dineflow.common.ApiException;
import com.dineflow.common.Body;
import com.dineflow.common.Input;
import com.dineflow.entity.AppNotification;
import com.dineflow.entity.User;
import com.dineflow.repository.AppNotificationRepository;
import com.dineflow.repository.UserRepository;
import com.dineflow.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.dineflow.common.ApiResponse.ok;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final UserRepository users;
    private final AppNotificationRepository notifications;

    /**
     * POST /api/notifications/fcm-token
     * Mobile app gửi FCM device token lên sau khi đăng nhập để nhận Push Notification.
     */
    @PostMapping("/fcm-token")
    public ResponseEntity<Map<String, Object>> updateFcmToken(@CurrentUser User user, @Body Map<String, Object> body) {
        Input in = Input.of(body);
        in.field("token").required("Thiếu token thiết bị FCM.").string().max(500);
        in.validate();

        String token = in.str("token");
        user.setFcmToken(token);
        users.save(user);

        log.info("[FCM] Đã cập nhật token thiết bị cho user ID {}: {}...", user.getId(), token.substring(0, Math.min(20, token.length())));
        return ok(Map.of("success", true), "Cập nhật token thông báo thành công.");
    }

    /**
     * GET /api/notifications
     * Lấy danh sách thông báo in-app của khách hàng.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> index(@CurrentUser User user) {
        List<AppNotification> list = notifications.findByCustomerIdOrderByCreatedAtDescIdDesc(user.getId());

        List<Map<String, Object>> dtos = list.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("title", n.getTitle());
            m.put("body", n.getBody());
            m.put("data", n.getData());
            m.put("read_at", n.getReadAt());
            m.put("created_at", n.getCreatedAt());
            return m;
        }).toList();

        return ok(dtos);
    }

    /**
     * PUT /api/notifications/{id}/read
     * Đánh dấu đã đọc thông báo.
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(@CurrentUser User user, @PathVariable Long id) {
        AppNotification n = notifications.findById(id).orElseThrow(ApiException::notFound);
        if (!n.getCustomerId().equals(user.getId())) {
            throw ApiException.forbidden("Bạn không có quyền thao tác trên thông báo này.");
        }
        n.setReadAt(Instant.now());
        notifications.save(n);
        return ok(Map.of("id", n.getId(), "read_at", n.getReadAt()), "Đã đánh dấu đã đọc.");
    }
}
