package com.dineflow.service;

import com.dineflow.common.ApiException;
import com.dineflow.entity.PasswordResetOtp;
import com.dineflow.entity.User;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.PasswordResetOtpRepository;
import com.dineflow.repository.UserRepository;
import com.dineflow.security.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quên mật khẩu bằng mã OTP gửi qua email — chỉ dành cho tài khoản KHÁCH HÀNG.
 * - Mã 6 chữ số, hiệu lực 10 phút.
 * - Nhập sai tối đa 5 lần; sai lần thứ 5 thì mã bị hủy.
 * - Mỗi email chỉ xin mã được 1 lần / 60 giây.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetOtpService {
    public static final int CODE_LENGTH = 6;
    public static final int EXPIRES_MINUTES = 10;
    public static final int MAX_ATTEMPTS = 5;
    public static final int RESEND_SECONDS = 60;

    private final UserRepository users;
    private final PasswordResetOtpRepository otps;
    private final TokenService tokens;
    private final MailService mail;
    private final BCryptPasswordEncoder encoder;

    /** email -> thời điểm được xin mã lại (RateLimiter của Laravel). */
    private final Map<String, Instant> resendAt = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** Lỗi khi máy chủ mail hỏng (controller trả 503). */
    public static class MailFailedException extends RuntimeException {
        public MailFailedException(String m) {
            super(m);
        }
    }

    /**
     * Gửi mã tới email. Email không thuộc khách hàng đang hoạt động thì KHÔNG gửi
     * nhưng vẫn trả lời như thường, để người ngoài không dò được email nào có tài khoản.
     */
    @Transactional
    public void send(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();
        Instant until = resendAt.get(email);
        if (until != null && until.isAfter(Instant.now())) {
            long seconds = Math.max(1, Instant.now().until(until, ChronoUnit.SECONDS));
            String msg = "Vui lòng đợi " + seconds + " giây rồi gửi lại mã.";
            Map<String, List<String>> errors = new LinkedHashMap<>();
            errors.put("email", List.of(msg));
            errors.put("retry_after", List.of(String.valueOf(seconds)));
            throw new ApiException(422, msg, errors);
        }
        resendAt.put(email, Instant.now().plusSeconds(RESEND_SECONDS));

        User user = findCustomer(email);
        if (user == null) return;

        String code = String.format("%0" + CODE_LENGTH + "d", random.nextInt((int) Math.pow(10, CODE_LENGTH)));
        // Mỗi tài khoản 1 mã: xin mã mới thì mã cũ hết tác dụng, số lần sai đếm lại từ 0.
        PasswordResetOtp otp = otps.findByUser_Id(user.getId()).orElseGet(PasswordResetOtp::new);
        otp.setUser(user);
        otp.setCodeHash(encoder.encode(code));
        otp.setAttempts(0);
        otp.setExpiresAt(Instant.now().plus(EXPIRES_MINUTES, ChronoUnit.MINUTES));
        otps.save(otp);

        try {
            mail.sendPasswordOtp(user.getEmail(), user.getName(), code, EXPIRES_MINUTES);
        } catch (Exception e) {
            log.error("Gửi email OTP thất bại", e);
            otps.delete(otp);
            resendAt.remove(email); // gửi hỏng thì cho thử lại ngay
            throw new MailFailedException("Không gửi được email lúc này. Vui lòng thử lại sau.");
        }
    }

    /** Kiểm tra mã (không dùng hết mã). Sai thì tăng số lần sai; sai đủ 5 lần thì hủy mã. */
    @Transactional(noRollbackFor = ApiException.class)
    public PasswordResetOtp verify(String email, String code) {
        User user = findCustomer(email.trim().toLowerCase());
        PasswordResetOtp otp = user == null ? null : otps.findByUser_Id(user.getId()).orElse(null);
        if (otp == null) throw fail("Mã xác nhận không đúng hoặc đã hết hạn. Vui lòng lấy mã mới.");
        if (otp.isExpired()) {
            otps.delete(otp);
            throw fail("Mã xác nhận đã hết hạn. Vui lòng lấy mã mới.");
        }
        if (!encoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otps.save(otp);
            if (otp.getAttempts() >= MAX_ATTEMPTS) {
                otps.delete(otp);
                throw fail("Bạn đã nhập sai " + MAX_ATTEMPTS + " lần. Mã đã bị hủy, vui lòng lấy mã mới.");
            }
            int left = MAX_ATTEMPTS - otp.getAttempts();
            throw fail("Mã xác nhận không đúng. Bạn còn " + left + " lần thử.");
        }
        return otp;
    }

    /** Đặt mật khẩu mới sau khi kiểm tra lại mã; xóa mã và đăng xuất mọi thiết bị. */
    @Transactional(noRollbackFor = ApiException.class)
    public void reset(String email, String code, String password) {
        PasswordResetOtp otp = verify(email, code);
        User user = otp.getUser();
        user.setPassword(encoder.encode(password));
        users.save(user);
        tokens.deleteAll(user.getId());
        otps.delete(otp);
    }

    private User findCustomer(String email) {
        User u = users.findByEmailLower(email).orElse(null);
        return u != null && u.getRole() == UserRole.CUSTOMER && u.isActive() ? u : null;
    }

    private static ApiException fail(String message) {
        return ApiException.validation("code", message);
    }
}
