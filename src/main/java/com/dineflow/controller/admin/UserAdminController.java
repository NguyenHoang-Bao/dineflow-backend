package com.dineflow.controller.admin;

import com.dineflow.common.Body;
import com.dineflow.common.ApiException;
import com.dineflow.common.Input;
import com.dineflow.entity.User;
import com.dineflow.enums.UserRole;
import com.dineflow.enums.UserStatus;
import com.dineflow.repository.UserRepository;
import com.dineflow.security.CurrentUser;
import com.dineflow.security.Roles;
import com.dineflow.security.TokenService;
import com.dineflow.service.Resources;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.dineflow.common.ApiResponse.error;
import static com.dineflow.common.ApiResponse.ok;

/**
 * Quản lý tài khoản (Admin). Không xóa cứng: "xóa" = khóa tài khoản.
 * Tài khoản nhân viên mới có mật khẩu mặc định 123456.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Roles(UserRole.ADMIN)
public class UserAdminController {
    private static final List<String> STAFF_ROLES = List.of(UserRole.ORDER_STAFF.value(), UserRole.CASHIER.value());

    private final UserRepository users;
    private final TokenService tokens;
    private final BCryptPasswordEncoder encoder;
    private final Resources res;

    /** GET /api/admin/users?role=&status=&search= */
    @GetMapping
    public ResponseEntity<Map<String, Object>> index(@RequestParam Map<String, String> query) {
        Input in = Input.ofQuery(query);
        in.field("role").enumOf(UserRole.class);
        in.field("status").enumOf(UserStatus.class);
        in.field("search").max(100);
        in.validate();

        Specification<User> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (in.filled("role")) p.add(cb.equal(root.get("role"), UserRole.of(in.str("role"))));
            if (in.filled("status")) p.add(cb.equal(root.get("status"), UserStatus.of(in.str("status"))));
            if (in.filled("search")) {
                String s = "%" + in.str("search") + "%";
                p.add(cb.or(cb.like(root.get("name"), s), cb.like(root.get("email"), s)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        // Giống ORDER BY role, name trên cột ENUM của MySQL: admin → order_staff → cashier → customer, rồi theo tên
        List<User> list = new ArrayList<>(users.findAll(spec, Sort.by("name")));
        list.sort(java.util.Comparator.comparingInt(u -> u.getRole().ordinal()));
        return ok(list.stream().map(res::userAdmin).toList());
    }

    /** POST /api/admin/users — tạo tài khoản NV Order / Thu ngân, mật khẩu mặc định 123456. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@Body Map<String, Object> body) {
        Input in = Input.of(body);
        in.field("name").required("Vui lòng nhập họ tên.").string().max(100);
        in.field("email").required("Vui lòng nhập email.").email("Email không đúng định dạng.").max(150)
                .check(v -> !users.existsByEmail(v.toString()), "Email này đã được sử dụng.");
        in.field("phone").string().max(20);
        in.field("role").required("Vui lòng chọn vai trò.").in(STAFF_ROLES, "Vai trò phải là NV Order hoặc Thu ngân.");
        in.validate();

        User u = new User();
        u.setName(in.str("name"));
        u.setEmail(in.str("email"));
        u.setPhone(in.filled("phone") ? in.str("phone") : null);
        u.setRole(UserRole.of(in.str("role")));
        u.setPassword(encoder.encode(User.DEFAULT_PASSWORD));
        u.setStatus(UserStatus.ACTIVE);
        users.save(u);
        return ok(res.userAdmin(u), "Tạo tài khoản thành công", 201);
    }

    /** PUT /api/admin/users/{id} — không đổi mật khẩu ở đây. */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Body Map<String, Object> body) {
        User u = users.findById(id).orElseThrow(ApiException::notFound);
        Input in = Input.of(body);
        if (in.has("name")) in.field("name").required("Vui lòng nhập họ tên.").string().max(100);
        if (in.has("email")) in.field("email").required("Vui lòng nhập email.").email("Email không đúng định dạng.").max(150)
                .check(v -> !users.existsByEmailAndIdNot(v.toString(), id), "Email này đã được sử dụng.");
        in.field("phone").string().max(20);
        in.field("role").in(STAFF_ROLES, "Vai trò phải là NV Order hoặc Thu ngân.");
        in.validate();

        if (in.has("name")) u.setName(in.str("name"));
        if (in.has("email")) u.setEmail(in.str("email"));
        if (in.has("phone")) u.setPhone(in.filled("phone") ? in.str("phone") : null);
        if (in.filled("role")) u.setRole(UserRole.of(in.str("role")));
        users.save(u);
        return ok(res.userAdmin(u), "Cập nhật thành công");
    }

    /** PATCH /api/admin/users/{id}/reset-password — đặt lại mật khẩu NV về 123456, đăng xuất mọi thiết bị. */
    @PatchMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@PathVariable Long id) {
        User u = users.findById(id).orElseThrow(ApiException::notFound);
        if (!u.hasRole(UserRole.ORDER_STAFF, UserRole.CASHIER)) {
            throw ApiException.validation("user", u.getRole() == UserRole.CUSTOMER
                    ? "Khách hàng tự đặt lại mật khẩu bằng chức năng \"Quên mật khẩu\" trên app."
                    : "Chỉ đặt lại được mật khẩu của NV Order và Thu ngân.");
        }
        u.setPassword(encoder.encode(User.DEFAULT_PASSWORD));
        users.save(u);
        tokens.deleteAll(u.getId());
        return ok(null, "Đã đặt lại mật khẩu của " + u.getName() + " về " + User.DEFAULT_PASSWORD + ". Nhân viên đăng nhập lại và tự đổi mật khẩu.");
    }

    /** PATCH /api/admin/users/{id}/toggle-status — khóa / kích hoạt. Không tự khóa mình. */
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<Map<String, Object>> toggleStatus(@CurrentUser User me, @PathVariable Long id) {
        User u = users.findById(id).orElseThrow(ApiException::notFound);
        if (Objects.equals(u.getId(), me.getId())) return error("Không thể khóa tài khoản của chính mình", 422, null);
        u.setStatus(u.getStatus() == UserStatus.ACTIVE ? UserStatus.LOCKED : UserStatus.ACTIVE);
        users.save(u);
        String message = u.getStatus() == UserStatus.LOCKED
                ? "Đã khóa tài khoản " + u.getName()
                : "Đã kích hoạt tài khoản " + u.getName();
        return ok(res.userAdmin(u), message);
    }

    /** DELETE /api/admin/users/{id} — thực chất chỉ KHÓA tài khoản. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> destroy(@CurrentUser User me, @PathVariable Long id) {
        User u = users.findById(id).orElseThrow(ApiException::notFound);
        if (Objects.equals(u.getId(), me.getId())) return error("Không thể xóa tài khoản của chính mình", 422, null);
        u.setStatus(UserStatus.LOCKED);
        users.save(u);
        return ok(null, "Đã khóa tài khoản " + u.getName());
    }
}
