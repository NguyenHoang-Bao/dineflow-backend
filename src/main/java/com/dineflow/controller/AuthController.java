package com.dineflow.controller;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Input;
import com.dineflow.entity.PersonalAccessToken;
import com.dineflow.entity.User;
import com.dineflow.enums.UserRole;
import com.dineflow.enums.UserStatus;
import com.dineflow.repository.UserRepository;
import com.dineflow.security.*;
import com.dineflow.service.Resources;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository users;
    private final TokenService tokens;
    private final BCryptPasswordEncoder encoder;
    private final Resources res;

    /** POST /api/login — đăng nhập bằng email hoặc số điện thoại (gửi vào `login`, hoặc `email`). */
    @Public
    @Throttle
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Body Map<String, Object> body) {
        Input in = Input.of(body);
        in.field("login").requiredWithout("email", "Vui lòng nhập email hoặc số điện thoại.").string().max(255);
        in.field("email").requiredWithout("login", "Vui lòng nhập email hoặc số điện thoại.").string().max(255);
        in.field("password").required("Vui lòng nhập mật khẩu.").string();
        in.field("device_name").string().max(100);
        in.validate();

        String identifier = (in.filled("login") ? in.str("login") : in.str("email")).trim();
        User user = findByIdentifier(identifier);
        if (user == null || !encoder.matches(in.str("password"), user.getPassword())) {
            return error("Email hoặc mật khẩu không đúng.", 401, null);
        }
        if (!user.isActive()) {
            return error("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản lý.", 403, null);
        }
        return ok(tokenPayload(user, device(in)), "Đăng nhập thành công.");
    }

    /** POST /api/register — đăng ký tài khoản khách hàng. */
    @Public
    @Throttle
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Body Map<String, Object> body) {
        Input in = Input.of(body);
        in.field("name").required("Vui lòng nhập họ tên.").string().max(255);
        in.field("email").required("Vui lòng nhập email.").email("Email không đúng định dạng.").max(255)
                .check(v -> !users.existsByEmail(v.toString()), "Email này đã được sử dụng.");
        in.field("phone").string().max(20)
                .check(v -> !users.existsByPhone(v.toString()), "Số điện thoại này đã được sử dụng.");
        in.field("password").required("Vui lòng nhập mật khẩu.").string()
                .min(6, "Mật khẩu phải có ít nhất 6 ký tự.").max(100);
        in.field("device_name").string().max(100);
        in.validate();

        User user = new User();
        user.setName(in.str("name"));
        user.setEmail(in.str("email"));
        user.setPhone(in.filled("phone") ? in.str("phone") : null);
        user.setPassword(encoder.encode(in.str("password")));
        user.setRole(UserRole.CUSTOMER); // đăng ký công khai luôn là khách hàng
        user.setStatus(UserStatus.ACTIVE);
        users.save(user);
        user.setMemberCode(String.format("DF-MB%04d", user.getId()));
        users.save(user);

        return ok(tokenPayload(user, device(in)), "Đăng ký thành công.", 201);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        tokens.delete(AuthInterceptor.currentToken(request));
        return ok(null, "Đã đăng xuất.");
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@CurrentUser User user) {
        return ok(res.user(user));
    }

    /**
     * PATCH /api/me/password — tự đổi mật khẩu (mọi vai trò).
     * Đổi xong thì thu hồi mọi token khác, giữ token của máy đang dùng.
     */
    @PatchMapping("/me/password")
    public ResponseEntity<Map<String, Object>> changePassword(@CurrentUser User user, HttpServletRequest request,
                                                              @Body Map<String, Object> body) {
        Input in = Input.of(body);
        in.field("current_password").required("Vui lòng nhập mật khẩu hiện tại.").string();
        in.field("password").required("Vui lòng nhập mật khẩu mới.").string()
                .min(6, "Mật khẩu mới phải có ít nhất 6 ký tự.").max(100)
                .confirmed("Mật khẩu xác nhận không khớp.")
                .different("current_password", "Mật khẩu mới phải khác mật khẩu hiện tại.");
        in.validate();

        if (!encoder.matches(in.str("current_password"), user.getPassword())) {
            String message = "Mật khẩu hiện tại không đúng.";
            Map<String, List<String>> errors = new LinkedHashMap<>();
            errors.put("current_password", List.of(message));
            throw new ApiException(422, message, errors);
        }
        user.setPassword(encoder.encode(in.str("password")));
        users.save(user);
        PersonalAccessToken current = AuthInterceptor.currentToken(request);
        tokens.deleteOthers(user.getId(), current.getId());
        return ok(null, "Đổi mật khẩu thành công.");
    }

    /** Có @ thì tìm theo email, ngược lại theo số điện thoại (bỏ khoảng trắng, dấu chấm, gạch). */
    private User findByIdentifier(String identifier) {
        if (identifier.contains("@")) return users.findByEmail(identifier).orElse(null);
        String phone = identifier.replaceAll("\\D+", "");
        return phone.isEmpty() ? null : users.findByPhone(phone).orElse(null);
    }

    private static String device(Input in) {
        return in.filled("device_name") ? in.str("device_name") : "mobile";
    }

    private Map<String, Object> tokenPayload(User user, String device) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", tokens.createToken(user, device));
        m.put("token_type", "Bearer");
        m.put("user", res.user(user));
        return m;
    }
}
