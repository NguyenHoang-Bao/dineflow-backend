package com.dineflow.controller;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Db;
import com.dineflow.common.Input;
import com.dineflow.common.Times;
import com.dineflow.entity.*;
import com.dineflow.enums.*;
import com.dineflow.repository.*;
import com.dineflow.security.CurrentUser;
import com.dineflow.security.Roles;
import com.dineflow.service.LoyaltyRules;
import com.dineflow.service.OrderService;
import com.dineflow.service.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.dineflow.common.ApiResponse.ok;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class CheckoutController {
    private final OrderRepository orders;
    private final ShiftRepository shifts;
    private final PromotionRepository promotions;
    private final UserRepository users;
    private final InvoiceRepository invoices;
    private final OrderService orderService;
    private final Resources res;
    private final TransactionTemplate tx;
    private final Db db;

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * POST /api/orders/{id}/pay
     * body: { "payment_method": "cash|qr|card", "promotion_code": "WELCOME10", "member_code": "DF-MB0001" }
     *
     * Quy tắc Kiểu A:
     * - Giảm = max(giảm theo hạng, giảm theo voucher), không cộng dồn.
     * - Cộng điểm theo số tiền thực trả. Thu ngân phải có ca đang mở.
     * - Tiền cọc (nếu có) khấu trừ SAU khi tính giảm giá.
     */
    @Roles({UserRole.CASHIER, UserRole.ADMIN})
    @PostMapping("/{id}/pay")
    public ResponseEntity<Map<String, Object>> pay(@CurrentUser User cashier, @PathVariable Long id,
                                                   @Body Map<String, Object> body) {
        Order found = orders.findById(id).orElseThrow(ApiException::notFound);
        Input in = Input.of(body);
        in.field("payment_method").required("Vui lòng chọn phương thức thanh toán.")
                .enumOf(PaymentMethod.class, "Phương thức thanh toán không hợp lệ.");
        in.field("promotion_code").string().max(30);
        in.field("member_code").string().max(30);
        in.validate();

        Invoice invoice = tx.execute(status -> {
            // 1. Kiểm tra ca đang mở
            List<Shift> open = shifts.lockOpenOf(cashier.getId(), ShiftStatus.OPEN);
            if (open.isEmpty()) {
                throw ApiException.validation("shift", "Bạn chưa mở ca làm việc. Vui lòng mở ca trước khi thanh toán.");
            }
            Shift shift = open.get(0);

            // 2. Kiểm tra đơn (hỗ trợ trường hợp SePay webhook đã cập nhật paymentStatus=PAID trước khi thu ngân chốt hóa đơn)
            Order order = db.lock(found);
            boolean alreadyInvoiced = invoices.findByOrder_Id(order.getId()).isPresent();
            if (alreadyInvoiced) throw ApiException.validation("order", "Đơn này đã được thanh toán và xuất hóa đơn.");
            if (order.getStatus() == OrderStatus.CANCELLED) throw ApiException.validation("order", "Đơn đã hủy, không thể thanh toán.");
            if (order.getItems().isEmpty()) throw ApiException.validation("order", "Đơn chưa có món nào.");

            double subtotal = order.getTotalAmount().doubleValue();

            // 3. Giảm theo voucher
            Promotion promotion = null;
            double promoDiscount = 0.0;
            if (in.filled("promotion_code")) {
                promotion = promotions.findByCode(in.str("promotion_code").trim().toUpperCase())
                        .orElseThrow(() -> ApiException.validation("promotion_code", "Mã khuyến mãi không tồn tại."));
                if (!promotion.isApplicableTo(subtotal)) {
                    throw ApiException.validation("promotion_code", "Mã " + promotion.getCode()
                            + " đã hết hạn, chưa bắt đầu hoặc đơn chưa đạt tối thiểu " + Times.money(promotion.getMinOrderAmount().doubleValue()) + "đ.");
                }
                promoDiscount = promotion.calculateDiscount(subtotal);
            }

            // 4. Giảm theo hạng thành viên
            double tierDiscount = 0.0;
            User customer = order.getCustomer() == null ? null : db.lock(order.getCustomer());
            if (in.filled("member_code")) {
                String code = in.str("member_code").trim().toUpperCase();
                User member = users.findByMemberCode(code)
                        .filter(u -> u.getRole() == UserRole.CUSTOMER && u.getStatus() == UserStatus.ACTIVE)
                        .map(db::lock)
                        .orElseThrow(() -> ApiException.validation("member_code", "Không tìm thấy thành viên có mã \"" + in.str("member_code") + "\"."));
                if (customer != null && !Objects.equals(customer.getId(), member.getId())) {
                    throw ApiException.validation("member_code", "Đơn này đã gắn với khách " + customer.getName() + ", không đổi sang thành viên khác.");
                }
                if (customer == null) {
                    order.setCustomer(member);
                    customer = member;
                }
            }
            if (customer != null) {
                int tierPercent = LoyaltyRules.discountPercentFor(customer.getPoints());
                tierDiscount = Math.round(subtotal * tierPercent / 100.0 * 100) / 100.0;
            }

            // Lấy mức giảm CAO HƠN giữa hạng và voucher; hạng thắng thì không ghi promotion vào hóa đơn
            double discount = Math.max(promoDiscount, tierDiscount);
            if (tierDiscount > promoDiscount) promotion = null;

            // 5. Khấu trừ tiền cọc (nếu đơn gắn đặt bàn đã cọc)
            double deposit = 0.0;
            Reservation r = order.getReservation();
            if (r != null && r.isDepositPaid()) deposit = r.getDepositAmount().doubleValue();

            double total = Math.max(0.0, subtotal - discount - deposit);

            // 6. Tạo hóa đơn
            Invoice inv = invoices.findByOrder_Id(order.getId()).orElseGet(Invoice::new);
            inv.setOrder(order);
            inv.setCashier(cashier);
            inv.setShift(shift);
            inv.setPromotion(promotion);
            inv.setSubtotal(money(subtotal));
            inv.setDiscount(money(discount));
            inv.setDepositAmount(money(deposit));
            inv.setTotal(money(total));
            inv.setPaymentMethod(PaymentMethod.of(in.str("payment_method")));
            inv.setStatus(InvoiceStatus.PAID);
            inv.setPaidAt(Instant.now());
            invoices.save(inv);

            // 7. Đóng đơn và trả bàn
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setStatus(OrderStatus.COMPLETED);
            orders.save(order);
            orderService.releaseTableIfDone(order);

            // 8. Cộng điểm cho khách
            if (customer != null && total > 0) {
                int earned = LoyaltyRules.pointsEarned(total);
                if (earned > 0) {
                    customer.setPoints(customer.getPoints() + earned);
                    users.save(customer);
                }
            }
            return inv;
        });

        return ok(res.invoice(invoice), "Thanh toán thành công.");
    }
}
