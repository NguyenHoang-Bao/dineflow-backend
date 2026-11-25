package com.dineflow.controller.admin;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Input;
import com.dineflow.entity.Promotion;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.PromotionRepository;
import com.dineflow.security.Roles;
import com.dineflow.service.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static com.dineflow.common.ApiResponse.ok;

/** CRUD khuyến mãi (Admin). Không xóa cứng: chỉ bật/tắt is_active. */
@RestController
@RequestMapping("/api/admin/promotions")
@RequiredArgsConstructor
@Roles(UserRole.ADMIN)
public class PromotionAdminController {
    private final PromotionRepository promotions;
    private final Resources res;

    @GetMapping
    public ResponseEntity<Map<String, Object>> index() {
        return ok(promotions.findAllByOrderByValidFromDesc().stream().map(res::promotion).toList());
    }

    private void rules(Input in, Promotion existing) {
        boolean creating = existing == null;
        Input.Field code = in.field("code");
        Input.Field title = in.field("title");
        Input.Field percent = in.field("discount_percent");
        Input.Field from = in.field("valid_from");
        Input.Field to = in.field("valid_to");
        if (creating || in.has("code")) code.required("Vui lòng nhập mã khuyến mãi.");
        if (creating || in.has("title")) title.required("Vui lòng nhập tiêu đề.");
        if (creating || in.has("discount_percent")) percent.required("Vui lòng nhập % giảm.");
        if (creating) from.required("Vui lòng chọn ngày bắt đầu.");
        if (creating) to.required("Vui lòng chọn ngày kết thúc.");
        code.string().max(50).check(v -> creating
                ? !promotions.existsByCode(v.toString())
                : !promotions.existsByCodeAndIdNot(v.toString(), existing.getId()), "Mã khuyến mãi đã tồn tại.");
        title.string().max(200);
        in.field("description").string().max(500);
        percent.intBetween(1, 100);
        in.field("max_discount").numMin(0);
        in.field("min_order_amount").numMin(0);
        from.date();
        Instant fromValue = in.filled("valid_from") ? in.instant("valid_from") : (existing == null ? null : existing.getValidFrom());
        to.date();
        if (fromValue != null) to.after(fromValue, "Ngày kết thúc phải sau ngày bắt đầu.");
        in.field("is_active").bool();
        in.validate();
    }

    private void fill(Promotion p, Input in) {
        if (in.has("code")) p.setCode(in.str("code"));
        if (in.has("title")) p.setTitle(in.str("title"));
        if (in.has("description")) p.setDescription(in.str("description"));
        if (in.has("discount_percent")) p.setDiscountPercent(in.integer("discount_percent"));
        if (in.has("max_discount")) p.setMaxDiscount(in.num("max_discount") == null ? null : BigDecimal.valueOf(in.num("max_discount")));
        if (in.filled("min_order_amount")) p.setMinOrderAmount(BigDecimal.valueOf(in.num("min_order_amount")));
        if (in.filled("valid_from")) p.setValidFrom(in.instant("valid_from"));
        if (in.filled("valid_to")) p.setValidTo(in.instant("valid_to"));
        if (in.filled("is_active")) p.setIsActive(in.bool("is_active"));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@Body Map<String, Object> body) {
        Input in = Input.of(body);
        rules(in, null);
        Promotion p = new Promotion();
        fill(p, in);
        promotions.save(p);
        return ok(res.promotion(p), "Thêm khuyến mãi thành công", 201);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Body Map<String, Object> body) {
        Promotion p = promotions.findById(id).orElseThrow(ApiException::notFound);
        Input in = Input.of(body);
        rules(in, p);
        fill(p, in);
        promotions.save(p);
        return ok(res.promotion(p), "Cập nhật thành công");
    }

    /** Bật / tắt khuyến mãi; KHÔNG đổi ngày kết thúc để bật lại vẫn đúng thời hạn. */
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<Map<String, Object>> toggleActive(@PathVariable Long id) {
        Promotion p = promotions.findById(id).orElseThrow(ApiException::notFound);
        p.setIsActive(!p.getIsActive());
        promotions.save(p);
        String message = p.getIsActive()
                ? "Đã kích hoạt khuyến mãi \"" + p.getCode() + "\""
                : "Đã vô hiệu hóa khuyến mãi \"" + p.getCode() + "\"";
        return ok(res.promotion(p), message);
    }
}
