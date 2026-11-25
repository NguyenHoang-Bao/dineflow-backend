package com.dineflow.service;

import com.dineflow.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Gửi email. app.mail.mode=log thì chỉ in nội dung email ra console (giống MAIL_MAILER=log),
 * app.mail.mode=smtp thì gửi thật bằng spring.mail.*.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {
    private final AppProperties props;
    private final ObjectProvider<JavaMailSender> sender;

    public void send(String to, String subject, String html) throws Exception {
        if (!"smtp".equalsIgnoreCase(props.getMailMode())) {
            log.info("\n===== EMAIL (app.mail.mode=log, không gửi thật) =====\nTo: {}\nSubject: {}\n{}\n=====", to, subject, html);
            return;
        }
        JavaMailSender mail = sender.getObject();
        var message = mail.createMimeMessage();
        var helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(props.getMailFrom(), props.getMailFromName());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mail.send(message);
    }

    /** Email chứa mã OTP đặt lại mật khẩu (PasswordResetOtpNotification). */
    public void sendPasswordOtp(String to, String name, String code, int expiresInMinutes) throws Exception {
        String html = "<p>Xin chào " + escape(name) + ",</p>"
                + "<p>Bạn vừa yêu cầu đặt lại mật khẩu tài khoản DineFlow. Mã xác nhận của bạn là:</p>"
                + "<p style=\"font-size:24px\"><b>" + code + "</b></p>"
                + "<p>Mã có hiệu lực trong " + expiresInMinutes + " phút. Không chia sẻ mã này cho bất kỳ ai, kể cả nhân viên nhà hàng.</p>"
                + "<p>Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này — mật khẩu của bạn vẫn giữ nguyên.</p>"
                + "<p>DineFlow Restaurant</p>";
        send(to, "Mã đặt lại mật khẩu DineFlow: " + code, html);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
