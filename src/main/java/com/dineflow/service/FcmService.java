package com.dineflow.service;

import com.dineflow.config.FirebaseConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final FirebaseConfig firebaseConfig;

    /**
     * Gửi Push Notification tới thiết bị người dùng qua FCM Token.
     * Nếu chưa cấu hình Firebase thật hoặc chưa có token, hệ thống ghi log mô phỏng (Mock mode).
     */
    public boolean sendPush(String fcmToken, String title, String body, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.info("\n===== [FCM MOCK - CHƯA CÓ TOKEN THIẾT BỊ] =====\nTiêu đề: {}\nNội dung: {}\nData: {}\n==============================================",
                    title, body, data);
            return false;
        }

        if (!firebaseConfig.isInitialized()) {
            log.info("\n===== [FCM MOCK - CHƯA CÓ SERVICE ACCOUNT KEY] =====\nToken nhận: {}\nTiêu đề: {}\nNội dung: {}\nData: {}\n==================================================",
                    fcmToken, title, body, data);
            return false;
        }

        try {
            Message.Builder builder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(builder.build());
            log.info("[FCM] Đã gửi thông báo thành công: messageId = {}", response);
            return true;
        } catch (Exception e) {
            log.error("[FCM] Lỗi khi gửi Push Notification qua Firebase: {}", e.getMessage());
            return false;
        }
    }
}
