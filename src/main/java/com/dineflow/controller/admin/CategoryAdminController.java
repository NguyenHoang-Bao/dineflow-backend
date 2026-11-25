package com.dineflow.controller.admin;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Input;
import com.dineflow.entity.Category;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.CategoryRepository;
import com.dineflow.repository.FoodRepository;
import com.dineflow.security.Roles;
import com.dineflow.service.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

/** CRUD danh mục món (Admin). Chỉ xóa được danh mục không còn món. */
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@Roles(UserRole.ADMIN)
public class CategoryAdminController {
    private final CategoryRepository categories;
    private final FoodRepository foods;
    private final Resources res;

    @GetMapping
    public ResponseEntity<Map<String, Object>> index() {
        return ok(categories.findAllByOrderBySortOrderAscNameAsc().stream().map(res::category).toList());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@Body Map<String, Object> body) {
        Input in = Input.of(body);
        in.field("name").required("Vui lòng nhập tên danh mục.").string().max(100)
                .check(v -> !categories.existsByName(v.toString()), "Tên danh mục đã tồn tại.");
        in.field("icon").string().max(50);
        in.field("sort_order").intBetween(0, Integer.MAX_VALUE);
        in.validate();

        Category c = new Category();
        c.setName(in.str("name"));
        c.setIcon(in.str("icon"));
        c.setSortOrder(in.filled("sort_order") ? in.integer("sort_order") : categories.maxSortOrder() + 1);
        categories.save(c);
        return ok(res.category(c), "Thêm danh mục thành công", 201);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Body Map<String, Object> body) {
        Category c = categories.findById(id).orElseThrow(ApiException::notFound);
        Input in = Input.of(body);
        if (in.has("name")) in.field("name").required("Vui lòng nhập tên danh mục.").string().max(100)
                .check(v -> !categories.existsByNameAndIdNot(v.toString(), id), "Tên danh mục đã tồn tại.");
        in.field("icon").string().max(50);
        in.field("sort_order").intBetween(0, Integer.MAX_VALUE);
        in.validate();

        if (in.has("name")) c.setName(in.str("name"));
        if (in.has("icon")) c.setIcon(in.str("icon"));
        if (in.filled("sort_order")) c.setSortOrder(in.integer("sort_order"));
        categories.save(c);
        return ok(res.category(c), "Cập nhật thành công");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> destroy(@PathVariable Long id) {
        Category c = categories.findById(id).orElseThrow(ApiException::notFound);
        long count = foods.countByCategoryId(c.getId());
        if (count > 0) {
            return error("Không thể xóa danh mục \"" + c.getName() + "\" vì còn " + count + " món. Hãy chuyển hoặc ẩn các món trước.", 422, null);
        }
        categories.delete(c);
        return ok(null, "Đã xóa danh mục \"" + c.getName() + "\"");
    }
}
