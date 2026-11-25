package com.dineflow.service;

import com.dineflow.common.Times;
import com.dineflow.config.AppProperties;
import com.dineflow.entity.*;
import com.dineflow.repository.DiningTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Biến entity thành JSON trả cho app — thay cho các class trong app/Http/Resources của Laravel.
 * Tên key giữ y nguyên bản PHP để app Flutter không phải sửa.
 */
@Component
@RequiredArgsConstructor
public class Resources {
    private final AppProperties props;
    private final DiningTableRepository tables;

    private static Double d(java.math.BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    /** UserResource */
    public Map<String, Object> user(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("role", u.getRole().value());
        m.put("role_label", u.getRole().label());
        m.put("status", u.getStatus().value());
        m.put("avatar_url", u.getAvatarUrl());
        m.put("member_code", u.getMemberCode());
        m.put("points", u.getPoints());
        return m;
    }

    /** UserAdminResource */
    public Map<String, Object> userAdmin(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone() == null ? "" : u.getPhone());
        m.put("role", u.getRole().value());
        m.put("status", u.getStatus().value());
        m.put("avatar_url", u.getAvatarUrl());
        m.put("member_code", u.getMemberCode());
        m.put("points", u.getPoints() == null ? 0 : u.getPoints());
        m.put("created_at", Times.iso(u.getCreatedAt()));
        return m;
    }

    /** CategoryResource */
    public Map<String, Object> category(Category c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("icon", c.getIcon());
        return m;
    }

    /** URL ảnh món: path trong storage → /storage/..., đã là http(s) thì giữ nguyên. */
    public String imageUrl(Food f) {
        String img = f.getImage();
        if (img == null || img.isBlank()) return null;
        return img.startsWith("http://") || img.startsWith("https://") ? img : props.storageUrl(img);
    }

    /** FoodResource (luôn kèm options). */
    public Map<String, Object> food(Food f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("category_id", f.getCategoryId());
        m.put("name", f.getName());
        m.put("price", d(f.getPrice()));
        m.put("description", f.getDescription() == null ? "" : f.getDescription());
        m.put("image", imageUrl(f));
        m.put("icon", f.getIcon());
        m.put("color", f.getColor());
        m.put("rating", d(f.getRating()));
        m.put("is_best_seller", f.getIsBestSeller());
        m.put("is_available", f.getIsAvailable());
        List<Map<String, Object>> options = new ArrayList<>();
        for (FoodOption o : f.getOptions()) {
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("id", o.getId());
            om.put("group", o.getGroup().value());
            om.put("name", o.getName());
            om.put("price_modifier", d(o.getPriceModifier()));
            options.add(om);
        }
        m.put("options", options);
        return m;
    }

    /** PromotionResource */
    public Map<String, Object> promotion(Promotion p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("code", p.getCode());
        m.put("title", p.getTitle());
        m.put("description", p.getDescription() == null ? "" : p.getDescription());
        m.put("discount_percent", p.getDiscountPercent());
        m.put("max_discount", d(p.getMaxDiscount()));
        m.put("min_order_amount", d(p.getMinOrderAmount()));
        m.put("valid_from", Times.iso(p.getValidFrom()));
        m.put("valid_to", Times.iso(p.getValidTo()));
        m.put("is_active", p.getIsActive());
        m.put("status", p.displayStatus());
        return m;
    }

    /** TableResource. extra: effective_status / next_reservation_at / next_reservation (có thể null). */
    public Map<String, Object> table(DiningTable t, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("number", t.getNumber());
        m.put("area", t.getArea());
        m.put("capacity", t.getCapacity());
        Object effective = extra == null ? null : extra.get("effective_status");
        m.put("status", effective != null ? effective : t.getStatus().value());
        m.put("serving_since", Times.iso(t.getServingSince()));
        m.put("current_order_id", t.getCurrentOrderId());
        m.put("next_reservation_at", extra == null ? null : extra.get("next_reservation_at"));
        m.put("next_reservation", extra == null ? null : extra.get("next_reservation"));
        m.put("is_locked", Boolean.TRUE.equals(t.getIsLocked()));
        m.put("is_active", Boolean.TRUE.equals(t.getIsActive()));
        return m;
    }

    public Map<String, Object> table(DiningTable t) {
        return table(t, null);
    }

    /** Các bàn của lượt đặt, theo đúng thứ tự trong table_ids. */
    public List<DiningTable> tablesOf(Reservation r) {
        List<Long> ids = r.getTableIds();
        Map<Long, DiningTable> byId = tables.findAllById(ids).stream()
                .collect(Collectors.toMap(DiningTable::getId, t -> t));
        List<DiningTable> out = new ArrayList<>();
        for (Long id : ids) if (byId.containsKey(id)) out.add(byId.get(id));
        return out;
    }

    /** ReservationResource. withCustomer = có kèm tên / SĐT khách (danh sách cho nhân viên). */
    public Map<String, Object> reservation(Reservation r, boolean withCustomer) {
        List<DiningTable> ts = tablesOf(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("code", r.getCode());
        m.put("customer_id", r.getCustomerId());
        if (withCustomer) {
            m.put("customer_name", r.getCustomer() == null ? null : r.getCustomer().getName());
            m.put("customer_phone", r.getCustomer() == null ? null : r.getCustomer().getPhone());
        }
        m.put("table_ids", r.getTableIds());
        m.put("table_numbers", ts.stream().map(DiningTable::getNumber).toList());
        m.put("area", r.getArea());
        m.put("capacity", ts.stream().mapToInt(DiningTable::getCapacity).sum());
        m.put("date_time", Times.iso(r.getDateTime()));
        m.put("guest_count", r.getGuestCount());
        m.put("note", r.getNote());
        m.put("status", r.getStatus().value());
        m.put("status_label", r.getStatus().label());
        m.put("is_held", r.isHeld());
        m.put("hold_expires_at", Times.iso(r.getHoldExpiresAt()));
        m.put("pre_order_items", r.getPreOrderItems() == null ? List.of() : r.getPreOrderItems());
        m.put("pre_order_payment", r.getPreOrderPayment() == null ? null : r.getPreOrderPayment().value());
        m.put("pre_order_total", r.preOrderTotal());
        m.put("deposit_amount", d(r.getDepositAmount()));
        m.put("deposit_paid_at", Times.iso(r.getDepositPaidAt()));
        m.put("is_deposit_paid", r.isDepositPaid());
        return m;
    }

    /** OrderItemResource */
    public Map<String, Object> orderItem(OrderItem i) {
        List<Map<String, Object>> options = i.getOptions() == null ? List.of() : i.getOptions();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("food_id", i.getFoodId());
        m.put("food_name", i.getFoodName());
        m.put("price", d(i.getPrice()));
        m.put("quantity", i.getQuantity());
        m.put("options", options);
        m.put("options_label", options.stream().map(o -> String.valueOf(o.get("name"))).collect(Collectors.joining(", ")));
        m.put("note", i.getNote() == null ? "" : i.getNote());
        m.put("unit_price", i.unitPrice());
        m.put("total", i.lineTotal());
        return m;
    }

    /** OrderResource (đã load đủ table, customer, staff, items, reservation, cancelledBy). */
    public Map<String, Object> order(Order o) {
        double deposit = 0.0;
        Reservation r = o.getReservation();
        if (r != null && r.isDepositPaid()) deposit = r.getDepositAmount().doubleValue();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("code", o.getCode());
        m.put("table_id", o.getTableId());
        m.put("table_number", o.getTable() == null ? null : o.getTable().getNumber());
        m.put("customer_id", o.getCustomerId());
        m.put("customer_name", o.getCustomer() == null ? null : o.getCustomer().getName());
        m.put("customer_points", o.getCustomer() == null ? null : o.getCustomer().getPoints());
        m.put("staff_id", o.getStaffId());
        m.put("staff_name", o.getStaff() == null ? null : o.getStaff().getName());
        m.put("status", o.getStatus().value());
        m.put("status_label", o.getStatus().label());
        m.put("payment_status", o.getPaymentStatus().value());
        m.put("payment_status_label", o.getPaymentStatus().label());
        m.put("note", o.getNote() == null ? "" : o.getNote());
        m.put("subtotal", d(o.getTotalAmount()));
        m.put("total_quantity", o.getItems().stream().mapToInt(OrderItem::getQuantity).sum());
        m.put("items", o.getItems().stream().map(this::orderItem).toList());
        m.put("reservation_id", o.getReservationId());
        m.put("deposit_amount", deposit);
        m.put("created_at", Times.iso(o.getCreatedAt()));
        m.put("cancel_reason", o.getCancelReason() == null ? null : o.getCancelReason().value());
        m.put("cancel_reason_label", o.getCancelReason() == null ? null : o.getCancelReason().label());
        m.put("cancel_note", o.getCancelNote());
        m.put("cancelled_by_name", o.getCancelledBy() == null ? null : o.getCancelledBy().getName());
        m.put("cancelled_at", Times.iso(o.getCancelledAt()));
        return m;
    }

    /** InvoiceResource */
    public Map<String, Object> invoice(Invoice inv) {
        Order o = inv.getOrder();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inv.getId());
        m.put("code", String.format("HD%05d", inv.getId()));
        m.put("order_id", o == null ? null : o.getId());
        m.put("order_code", o == null ? null : o.getCode());
        m.put("table_number", o == null || o.getTable() == null ? null : o.getTable().getNumber());
        m.put("items", o == null ? List.of() : o.getItems().stream().map(this::orderItem).toList());
        m.put("subtotal", d(inv.getSubtotal()));
        m.put("discount", d(inv.getDiscount()));
        m.put("deposit_amount", inv.getDepositAmount() == null ? 0.0 : inv.getDepositAmount().doubleValue());
        m.put("total", d(inv.getTotal()));
        m.put("promotion_code", inv.getPromotion() == null ? null : inv.getPromotion().getCode());
        m.put("payment_method", inv.getPaymentMethod() == null ? null : inv.getPaymentMethod().value());
        m.put("status", inv.getStatus().value());
        m.put("shift_id", inv.getShiftId());
        m.put("points_earned", LoyaltyRules.pointsEarned(inv.getTotal().doubleValue()));
        m.put("paid_at", Times.iso(inv.getPaidAt()));
        m.put("created_at", Times.iso(inv.getPaidAt() != null ? inv.getPaidAt() : inv.getCreatedAt()));
        m.put("cashier_id", inv.getCashierId());
        m.put("cashier_name", inv.getCashier() == null ? null : inv.getCashier().getName());
        return m;
    }
}
