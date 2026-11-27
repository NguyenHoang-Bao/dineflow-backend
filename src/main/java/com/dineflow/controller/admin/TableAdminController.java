package com.dineflow.controller.admin;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Input;
import com.dineflow.entity.DiningTable;
import com.dineflow.enums.TableStatus;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.DiningTableRepository;
import com.dineflow.security.Roles;
import com.dineflow.service.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

/**
 * CRUD bàn ăn (Admin). Không xóa cứng bàn.
 * is_locked: NV Order không mở được bàn (vẫn hiển thị). is_active: ẩn hẳn khỏi hệ thống.
 */
@RestController
@RequestMapping("/api/admin/tables")
@RequiredArgsConstructor
@Roles(UserRole.ADMIN)
public class TableAdminController {
    private final DiningTableRepository tables;
    private final Resources res;

    @GetMapping
    public ResponseEntity<Map<String, Object>> index(@RequestParam(required = false) String area) {
        List<DiningTable> list = area != null && !area.isBlank() ? tables.findByAreaOrderByIdAsc(area) : tables.findAllByOrderByIdAsc();
        return ok(list.stream().map(res::table).toList(), "Thành công", 200, Map.of("areas", tables.findAreas()));
    }

    private void rules(Input in, boolean creating) {
        Input.Field number = in.field("number");
        Input.Field area = in.field("area");
        Input.Field capacity = in.field("capacity");
        if (creating || in.has("number")) number.required("Vui lòng nhập số bàn.");
        if (creating || in.has("area")) area.required("Vui lòng nhập khu vực.");
        if (creating || in.has("capacity")) capacity.required("Vui lòng nhập sức chứa.");
        number.string().max(20);
        area.string().max(50);
        capacity.intBetween(1, 50);
        in.validate();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@Body Map<String, Object> body) {
        Input in = Input.of(body);
        rules(in, true);
        if (tables.findByNumber(in.str("number")).isPresent()) throw ApiException.validation("number", "Số bàn đã tồn tại.");
        DiningTable t = new DiningTable();
        t.setNumber(in.str("number"));
        t.setArea(in.str("area"));
        t.setCapacity(in.integer("capacity"));
        Double deposit = in.num("deposit_amount");
        t.setDepositAmount(deposit != null ? BigDecimal.valueOf(deposit) : BigDecimal.ZERO);
        t.setStatus(TableStatus.EMPTY);
        t.setIsLocked(false);
        t.setIsActive(true);
        tables.save(t);
        return ok(res.table(t), "Thêm bàn thành công", 201);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Body Map<String, Object> body) {
        DiningTable t = tables.findById(id).orElseThrow(ApiException::notFound);
        Input in = Input.of(body);
        rules(in, false);
        if (in.has("number")) {
            tables.findByNumber(in.str("number")).filter(o -> !o.getId().equals(id))
                    .ifPresent(o -> { throw ApiException.validation("number", "Số bàn đã tồn tại."); });
            t.setNumber(in.str("number"));
        }
        if (in.has("area")) t.setArea(in.str("area"));
        if (in.has("capacity")) t.setCapacity(in.integer("capacity"));
        if (in.has("deposit_amount")) {
            Double dep = in.num("deposit_amount");
            t.setDepositAmount(dep != null ? BigDecimal.valueOf(dep) : BigDecimal.ZERO);
        }
        tables.save(t);
        return ok(res.table(t), "Cập nhật thành công");
    }

    /** Khóa / mở khóa bàn. Bàn đang có khách vẫn khóa được (chỉ chặn mở bàn mới). */
    @PatchMapping("/{id}/toggle-locked")
    public ResponseEntity<Map<String, Object>> toggleLocked(@PathVariable Long id) {
        DiningTable t = tables.findById(id).orElseThrow(ApiException::notFound);
        t.setIsLocked(!t.getIsLocked());
        tables.save(t);
        return ok(res.table(t), t.getIsLocked() ? "Đã khóa bàn " + t.getNumber() : "Đã mở khóa bàn " + t.getNumber());
    }

    /** Vô hiệu hóa / kích hoạt bàn. Không cho vô hiệu hóa bàn đang có khách. */
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<Map<String, Object>> toggleActive(@PathVariable Long id) {
        DiningTable t = tables.findById(id).orElseThrow(ApiException::notFound);
        if (t.getIsActive() && t.getStatus() == TableStatus.SERVING) {
            return error("Không thể vô hiệu hóa bàn " + t.getNumber() + " đang có khách. Hãy xử lý đơn hàng trước.", 422, null);
        }
        t.setIsActive(!t.getIsActive());
        tables.save(t);
        return ok(res.table(t), t.getIsActive() ? "Đã kích hoạt bàn " + t.getNumber() : "Đã vô hiệu hóa bàn " + t.getNumber());
    }
}
