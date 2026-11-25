package com.dineflow.security;

import com.dineflow.common.ApiException;
import com.dineflow.entity.PersonalAccessToken;
import com.dineflow.entity.User;
import com.dineflow.enums.UserRole;
import com.dineflow.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thay cho middleware auth:sanctum + active + role + throttle của Laravel.
 * - @Public: bỏ qua đăng nhập.
 * - Còn lại: cần "Authorization: Bearer <token>", tài khoản không bị khóa.
 * - @Roles(...): chỉ các vai trò được liệt kê.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    public static final String USER_ATTR = "auth.user";
    public static final String TOKEN_ATTR = "auth.token";

    private final TokenService tokenService;
    private final UserRepository users;

    /** IP -> [bắt đầu cửa sổ (ms), số lần] */
    private final Map<String, long[]> hits = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) return true;

        Throttle throttle = hm.getMethodAnnotation(Throttle.class);
        if (throttle != null) checkThrottle(request, throttle.perMinute());

        if (hm.hasMethodAnnotation(Public.class)) return true;

        String header = request.getHeader("Authorization");
        String bearer = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7).trim() : null;
        PersonalAccessToken token = tokenService.find(bearer)
                .orElseThrow(() -> new ApiException(401, "Chưa đăng nhập hoặc token không hợp lệ."));
        User user = users.findById(token.getTokenableId())
                .orElseThrow(() -> new ApiException(401, "Chưa đăng nhập hoặc token không hợp lệ."));

        // Chặn tài khoản đã bị admin khóa, kể cả khi token cũ còn sót lại.
        if (!user.isActive()) {
            tokenService.delete(token);
            throw ApiException.forbidden("Tài khoản của bạn đã bị khóa.");
        }
        tokenService.touch(token);
        request.setAttribute(USER_ATTR, user);
        request.setAttribute(TOKEN_ATTR, token);

        Roles roles = hm.getMethodAnnotation(Roles.class);
        if (roles == null) roles = hm.getBeanType().getAnnotation(Roles.class);
        if (roles != null && Arrays.stream(roles.value()).noneMatch(r -> r == user.getRole())) {
            throw ApiException.forbidden("Vai trò của bạn không được phép thực hiện thao tác này.");
        }
        return true;
    }

    private void checkThrottle(HttpServletRequest request, int limit) {
        String ip = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        long[] slot = hits.compute(ip, (k, v) -> {
            if (v == null || now - v[0] >= 60_000) return new long[]{now, 1};
            v[1]++;
            return v;
        });
        if (slot[1] > limit) throw new ApiException(429, "Thao tác quá nhanh, vui lòng thử lại sau.");
    }

    /** Token đang dùng của request hiện tại (cho logout / đổi mật khẩu). */
    public static PersonalAccessToken currentToken(HttpServletRequest request) {
        return (PersonalAccessToken) request.getAttribute(TOKEN_ATTR);
    }

    public static boolean isRole(User user, UserRole... roles) {
        return user != null && user.hasRole(roles);
    }
}
