package com.dineflow.controller;

import com.dineflow.common.Input;
import com.dineflow.entity.Food;
import com.dineflow.repository.CategoryRepository;
import com.dineflow.repository.FoodRepository;
import com.dineflow.repository.PromotionRepository;
import com.dineflow.service.Resources;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.dineflow.common.ApiResponse.ok;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MenuController {
    private final CategoryRepository categories;
    private final FoodRepository foods;
    private final PromotionRepository promotions;
    private final Resources res;

    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> categories() {
        return ok(categories.findAllByOrderBySortOrderAscNameAsc().stream().map(res::category).toList());
    }

    /**
     * GET /api/foods?category_id=&search=&available=1&best_seller=1
     * Mặc định trả cả món hết hàng (is_available = false) để app hiển thị mờ đi.
     */
    @GetMapping("/foods")
    public ResponseEntity<Map<String, Object>> foods(@RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("category_id").integer();
        in.field("search").max(100);
        in.field("available").bool();
        in.field("best_seller").bool();
        in.validate();

        Specification<Food> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (in.filled("category_id")) p.add(cb.equal(root.get("categoryId"), in.lng("category_id")));
            if (in.bool("available")) p.add(cb.isTrue(root.get("isAvailable")));
            if (in.bool("best_seller")) p.add(cb.isTrue(root.get("isBestSeller")));
            if (in.filled("search")) {
                String like = "%" + in.str("search") + "%";
                p.add(cb.or(cb.like(root.get("name"), like), cb.like(root.get("description"), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        List<Food> list = foods.findAll(spec, Sort.by("categoryId").and(Sort.by("name")));
        return ok(list.stream().map(res::food).toList());
    }

    /** Voucher đang trong thời gian hiệu lực. */
    @GetMapping("/promotions")
    public ResponseEntity<Map<String, Object>> promotions() {
        return ok(promotions.findValid(Instant.now()).stream().map(res::promotion).toList());
    }
}
