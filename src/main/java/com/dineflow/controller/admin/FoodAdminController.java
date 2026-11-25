package com.dineflow.controller.admin;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Input;
import com.dineflow.entity.Food;
import com.dineflow.entity.Reservation;
import com.dineflow.enums.OrderStatus;
import com.dineflow.enums.PaymentStatus;
import com.dineflow.enums.ReservationStatus;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.*;
import com.dineflow.security.Roles;
import com.dineflow.service.Resources;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

/**
 * CRUD món ăn (Admin).
 *  - toggle-available: ẩn/hiện (mềm, dùng khi tạm hết nguyên liệu).
 *  - DELETE: xóa vĩnh viễn (bị chặn nếu món đang trong đơn chưa thanh toán / món đặt trước sắp tới).
 */
@RestController
@RequestMapping("/api/admin/foods")
@RequiredArgsConstructor
@Roles(UserRole.ADMIN)
public class FoodAdminController {
    private final FoodRepository foods;
    private final CategoryRepository categories;
    private final OrderItemRepository orderItems;
    private final ReservationRepository reservations;
    private final FavoriteRepository favorites;
    private final Resources res;

    /** GET /api/admin/foods?category_id=&search=&available= — gồm cả món đang ẩn. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> index(@RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("category_id").integer();
        in.field("search").max(100);
        in.field("available").bool();
        in.validate();

        Specification<Food> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (in.filled("category_id")) p.add(cb.equal(root.get("categoryId"), in.lng("category_id")));
            if (in.filled("available")) p.add(cb.equal(root.get("isAvailable"), in.bool("available")));
            if (in.filled("search")) p.add(cb.like(root.get("name"), "%" + in.str("search") + "%"));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return ok(foods.findAll(spec, Sort.by("categoryId").and(Sort.by("name"))).stream().map(res::food).toList());
    }

    private void rules(Input in, boolean creating) {
        Input.Field cat = in.field("category_id");
        Input.Field name = in.field("name");
        Input.Field price = in.field("price");
        if (creating || in.has("category_id")) cat.required("Vui lòng chọn danh mục.");
        if (creating || in.has("name")) name.required("Vui lòng nhập tên món.");
        if (creating || in.has("price")) price.required("Vui lòng nhập giá.");
        cat.integer().check(v -> categories.existsById(Input.toLong(v)), "Danh mục không tồn tại.");
        name.string().max(150);
        price.numMin(0);
        in.field("description").string().max(500);
        in.field("image_url").url().max(500);
        in.field("is_available").bool();
        in.field("is_best_seller").bool();
        in.validate();
    }

    private void fill(Food f, Input in) {
        if (in.has("category_id")) f.setCategoryId(in.lng("category_id"));
        if (in.has("name")) f.setName(in.str("name"));
        if (in.has("price")) f.setPrice(BigDecimal.valueOf(in.num("price")));
        if (in.has("description")) f.setDescription(in.str("description"));
        // App gửi image_url (link ảnh) → lưu vào cột image
        if (in.has("image_url")) f.setImage(in.str("image_url"));
        if (in.filled("is_available")) f.setIsAvailable(in.bool("is_available"));
        if (in.filled("is_best_seller")) f.setIsBestSeller(in.bool("is_best_seller"));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@Body Map<String, Object> body) {
        Input in = Input.of(body);
        rules(in, true);
        Food f = new Food();
        f.setIsAvailable(true);
        fill(f, in);
        foods.save(f);
        return ok(res.food(f), "Thêm món thành công", 201);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Body Map<String, Object> body) {
        Food f = foods.findById(id).orElseThrow(ApiException::notFound);
        Input in = Input.of(body);
        rules(in, false);
        fill(f, in);
        foods.save(f);
        return ok(res.food(f), "Cập nhật thành công");
    }

    @PatchMapping("/{id}/toggle-available")
    public ResponseEntity<Map<String, Object>> toggleAvailable(@PathVariable Long id) {
        Food f = foods.findById(id).orElseThrow(ApiException::notFound);
        f.setIsAvailable(!f.getIsAvailable());
        foods.save(f);
        String message = f.getIsAvailable()
                ? "Đã hiện món \"" + f.getName() + "\" trong thực đơn"
                : "Đã ẩn món \"" + f.getName() + "\" khỏi thực đơn";
        return ok(res.food(f), message);
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> destroy(@PathVariable Long id) {
        Food f = foods.findById(id).orElseThrow(ApiException::notFound);
        String reason = deletionBlockedReason(f);
        if (reason != null) return error(reason, 422, null);
        favorites.deleteByFood_Id(f.getId());
        foods.delete(f); // options xóa theo (cascade)
        return ok(null, "Đã xóa món \"" + f.getName() + "\"");
    }

    /**
     * Lý do không được xóa món (null = xóa được).
     * Hóa đơn cũ đã thanh toán không chặn vì dòng món giữ tên món (food_name).
     */
    private String deletionBlockedReason(Food f) {
        if (orderItems.existsInOpenOrder(f.getId(), PaymentStatus.PAID, OrderStatus.CANCELLED)) {
            return "Không thể xóa \"" + f.getName() + "\" vì món đang có trong đơn chưa thanh toán. Hãy ẩn món (hết món) thay vì xóa.";
        }
        for (Reservation r : reservations.findByStatusAndPreOrderItemsIsNotNull(ReservationStatus.UPCOMING)) {
            boolean contains = r.getPreOrderItems().stream()
                    .anyMatch(line -> line.get("food_id") instanceof Number n && Objects.equals(n.longValue(), f.getId()));
            if (contains) {
                return "Không thể xóa \"" + f.getName() + "\" vì khách đã đặt trước món này cho lượt đặt bàn sắp tới. Hãy ẩn món (hết món) thay vì xóa.";
            }
        }
        return null;
    }
}
