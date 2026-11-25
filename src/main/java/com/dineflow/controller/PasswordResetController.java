package com.dineflow.controller;

import com.dineflow.common.Body;
import com.dineflow.common.Input;
import com.dineflow.security.Public;
import com.dineflow.security.Throttle;
import com.dineflow.service.PasswordResetOtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

/**
 * Quên mật khẩu bằng OTP email (công khai, chỉ tài khoản khách hàng).
 *   POST /api/password/forgot  { email }
 *   POST /api/password/verify  { email, code }
 *   POST /api/password/reset   { email, code, password, password_confirmation }
 */
@RestController
@RequestMapping("/api/password")
@RequiredArgsConstructor
public class PasswordResetController {
    private final PasswordResetOtpService otp;

    @Public
    @Throttle
    @PostMapping("/forgot")
    public ResponseEntity<Map<String, Object>> forgot(@Body Map<String, Object> body) {
        Input in = Input.of(body);
        in.field("email").required("Vui lòng nhập email.").email("Email không đúng định dạng.").max(255);
        in.validate();
        try {
            otp.send(in.str("email"));
        } catch (PasswordResetOtpService.MailFailedException e) {
            return error(e.getMessage(), 503, null);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expires_in", PasswordResetOtpService.EXPIRES_MINUTES * 60);
        data.put("resend_in", PasswordResetOtpService.RESEND_SECONDS);
        return ok(data, "Nếu email thuộc một tài khoản khách hàng, mã xác nhận đã được gửi. Vui lòng kiểm tra hộp thư (cả mục Spam).");
    }

    @Public
    @Throttle
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@Body Map<String, Object> body) {
        Input in = Input.of(body);
        codeRules(in);
        in.validate();
        otp.verify(in.str("email"), in.str("code"));
        return ok(null, "Mã xác nhận đúng. Vui lòng đặt mật khẩu mới.");
    }

    @Public
    @Throttle
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(@Body Map<String, Object> body) {
        Input in = Input.of(body);
        codeRules(in);
        in.field("password").required("Vui lòng nhập mật khẩu mới.").string()
                .min(6, "Mật khẩu mới phải có ít nhất 6 ký tự.").max(100)
                .confirmed("Mật khẩu xác nhận không khớp.");
        in.validate();
        otp.reset(in.str("email"), in.str("code"), in.str("password"));
        return ok(null, "Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.");
    }

    private static void codeRules(Input in) {
        in.field("email").required("Vui lòng nhập email.").email("Email không đúng định dạng.").max(255);
        in.field("code").required("Vui lòng nhập mã xác nhận.")
                .digits(PasswordResetOtpService.CODE_LENGTH, "Mã xác nhận gồm " + PasswordResetOtpService.CODE_LENGTH + " chữ số.");
    }
}
