package com.dineflow.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Getter
    private boolean initialized = false;

    @PostConstruct
    public void init() {
        try {
            InputStream serviceAccount = null;

            // 1. Tìm trong classpath (src/main/resources/serviceAccountKey.json)
            ClassPathResource resource = new ClassPathResource("serviceAccountKey.json");
            if (resource.exists()) {
                serviceAccount = resource.getInputStream();
                log.info("[Firebase] Tìm thấy file cấu hình serviceAccountKey.json trong classpath.");
            } else {
                // 2. Tìm ở thư mục gốc chạy ứng dụng
                File rootFile = new File("serviceAccountKey.json");
                if (rootFile.exists()) {
                    serviceAccount = new FileInputStream(rootFile);
                    log.info("[Firebase] Tìm thấy file cấu hình serviceAccountKey.json ở thư mục gốc.");
                }
            }

            if (serviceAccount != null) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                }
                initialized = true;
                log.info("[Firebase] Khởi tạo Firebase Admin SDK thành công!");
            } else {
                log.warn("[Firebase] Chưa có file serviceAccountKey.json. Hệ thống sẽ chạy FCM ở chế độ Mock/Log (in log thông báo ra console).");
            }
        } catch (Exception e) {
            log.error("[Firebase] Lỗi khi khởi tạo Firebase: {}. Chuyển sang chế độ Mock/Log.", e.getMessage());
            initialized = false;
        }
    }
}
