package com.dineflow.service;

import com.dineflow.common.Times;
import com.dineflow.config.AppProperties;
import com.dineflow.entity.Reservation;
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

    /** Email xác nhận đặt bàn thành công gửi tới khách hàng. */
    public void sendReservationConfirmation(Reservation r, java.util.List<String> tableNumbers) throws Exception {
        String recipient = r.getRecipientEmail();
        if (recipient == null || recipient.isBlank()) {
            log.info("Không gửi email xác nhận đặt bàn #{}: Khách hàng không có email.", r.getCode());
            return;
        }

        String customerName = r.getCustomer() != null ? r.getCustomer().getName() : "Quý khách";
        String timeStr = Times.format(r.getDateTime(), "HH:mm, 'ngày' dd/MM/yyyy");
        String tablesStr = tableNumbers == null || tableNumbers.isEmpty() ? "Đang sắp xếp" : String.join(", ", tableNumbers);

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 12px; background-color: #ffffff;\">");
        sb.append("<div style=\"text-align: center; border-bottom: 2px solid #ea580c; padding-bottom: 15px; margin-bottom: 20px;\">");
        sb.append("<h2 style=\"color: #ea580c; margin: 0;\">DineFlow Restaurant</h2>");
        sb.append("<p style=\"color: #64748b; margin: 5px 0 0 0;\">Xác Nhận Đặt Bàn Thành Công</p>");
        sb.append("</div>");

        sb.append("<p>Xin chào <b>").append(escape(customerName)).append("</b>,</p>");
        sb.append("<p>Cảm ơn bạn đã lựa chọn DineFlow! Lượt đặt bàn của bạn đã được hệ thống xác nhận với các thông tin chi tiết dưới đây:</p>");

        sb.append("<table style=\"width: 100%; border-collapse: collapse; margin: 20px 0; background-color: #f8fafc; border-radius: 8px; overflow: hidden;\">");
        sb.append("<tr><td style=\"padding: 10px 15px; border-bottom: 1px solid #e2e8f0; color: #475569; width: 35%;\">Mã đặt bàn:</td><td style=\"padding: 10px 15px; border-bottom: 1px solid #e2e8f0; font-weight: bold; color: #ea580c;\">#").append(escape(r.getCode())).append("</td></tr>");
        sb.append("<tr><td style=\"padding: 10px 15px; border-bottom: 1px solid #e2e8f0; color: #475569;\">Thời gian hẹn:</td><td style=\"padding: 10px 15px; border-bottom: 1px solid #e2e8f0; font-weight: bold;\">").append(escape(timeStr)).append("</td></tr>");
        sb.append("<tr><td style=\"padding: 10px 15px; border-bottom: 1px solid #e2e8f0; color: #475569;\">Khu vực & Bàn:</td><td style=\"padding: 10px 15px; border-bottom: 1px solid #e2e8f0;\">").append(escape(r.getArea())).append(" (Bàn: ").append(escape(tablesStr)).append(")</td></tr>");
        sb.append("<tr><td style=\"padding: 10px 15px; border-bottom: 1px solid #e2e8f0; color: #475569;\">Số lượng khách:</td><td style=\"padding: 10px 15px; border-bottom: 1px solid #e2e8f0;\">").append(r.getGuestCount()).append(" người</td></tr>");

        if (r.getNote() != null && !r.getNote().isBlank()) {
            sb.append("<tr><td style=\"padding: 10px 15px; border-bottom: 1px solid #e2e8f0; color: #475569;\">Ghi chú:</td><td style=\"padding: 10px 15px; border-bottom: 1px solid #e2e8f0;\">").append(escape(r.getNote())).append("</td></tr>");
        }

        if (r.isDepositPaid() && r.getDepositAmount() != null) {
            sb.append("<tr><td style=\"padding: 10px 15px; color: #475569;\">Tiền cọc đã trả:</td><td style=\"padding: 10px 15px; font-weight: bold; color: #16a34a;\">").append(Times.money(r.getDepositAmount().doubleValue())).append("đ (Đã thanh toán)</td></tr>");
        }
        sb.append("</table>");

        if (r.getPreOrderItems() != null && !r.getPreOrderItems().isEmpty()) {
            sb.append("<h4 style=\"color: #1e293b; margin: 15px 0 10px 0;\">Món Đặt Trước:</h4>");
            sb.append("<table style=\"width: 100%; border-collapse: collapse; margin-bottom: 20px; font-size: 14px;\">");
            sb.append("<tr style=\"background-color: #f1f5f9; text-align: left;\"><th style=\"padding: 8px;\">Món</th><th style=\"padding: 8px; text-align: center;\">SL</th><th style=\"padding: 8px; text-align: right;\">Thành tiền</th></tr>");
            for (java.util.Map<String, Object> item : r.getPreOrderItems()) {
                String foodName = String.valueOf(item.get("food_name"));
                int qty = ((Number) item.getOrDefault("quantity", 1)).intValue();
                double lineTotal = ((Number) item.getOrDefault("total", 0)).doubleValue();
                sb.append("<tr style=\"border-bottom: 1px solid #e2e8f0;\">");
                sb.append("<td style=\"padding: 8px;\">").append(escape(foodName)).append("</td>");
                sb.append("<td style=\"padding: 8px; text-align: center;\">").append(qty).append("</td>");
                sb.append("<td style=\"padding: 8px; text-align: right;\">").append(Times.money(lineTotal)).append("đ</td>");
                sb.append("</tr>");
            }
            sb.append("</table>");
        }

        sb.append("<div style=\"background-color: #fff7ed; border-left: 4px solid #ea580c; padding: 12px; margin: 20px 0; font-size: 13px; color: #9a3412;\">");
        sb.append("<b>Lưu ý:</b> Quý khách vui lòng có mặt đúng giờ. Nhà hàng giữ bàn tối đa <b>15 phút</b> sau giờ hẹn. Nếu có thay đổi, vui lòng liên hệ hotline nhà hàng để được hỗ trợ.");
        sb.append("</div>");

        sb.append("<p style=\"margin-top: 25px; color: #64748b; font-size: 13px; text-align: center;\">Đây là email tự động, vui lòng không trả lời trực tiếp email này.<br>Chúc bạn có một bữa ăn ngon miệng tại DineFlow!</p>");
        sb.append("</div>");

        send(recipient, "[DineFlow] Xác nhận đặt bàn thành công - Mã #" + r.getCode(), sb.toString());
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
