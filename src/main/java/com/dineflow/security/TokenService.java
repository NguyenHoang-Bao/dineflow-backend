package com.dineflow.security;

import com.dineflow.entity.PersonalAccessToken;
import com.dineflow.entity.User;
import com.dineflow.repository.PersonalAccessTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Token đăng nhập kiểu Laravel Sanctum: app nhận "{id}|{40 ký tự ngẫu nhiên}",
 * DB lưu SHA-256 của phần ngẫu nhiên. Token cũ tạo bởi bản Laravel vẫn dùng được.
 */
@Service
@RequiredArgsConstructor
public class TokenService {
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PersonalAccessTokenRepository tokens;

    public String createToken(User user, String deviceName) {
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < 40; i++) plain.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        PersonalAccessToken t = new PersonalAccessToken();
        t.setTokenableId(user.getId());
        t.setName(deviceName);
        t.setToken(sha256(plain.toString()));
        tokens.save(t);
        return t.getId() + "|" + plain;
    }

    /** Tìm token từ header Bearer. */
    public Optional<PersonalAccessToken> find(String bearer) {
        if (bearer == null || bearer.isBlank()) return Optional.empty();
        String plain = bearer;
        Long id = null;
        int bar = bearer.indexOf('|');
        if (bar > 0) {
            try {
                id = Long.parseLong(bearer.substring(0, bar));
            } catch (NumberFormatException ignored) {
            }
            plain = bearer.substring(bar + 1);
        }
        String hash = sha256(plain);
        Optional<PersonalAccessToken> found = id != null
                ? tokens.findById(id).filter(t -> MessageDigest.isEqual(t.getToken().getBytes(), hash.getBytes()))
                : tokens.findByToken(hash);
        return found.filter(t -> PersonalAccessToken.USER_TYPE.equals(t.getTokenableType()))
                .filter(t -> t.getExpiresAt() == null || t.getExpiresAt().isAfter(Instant.now()));
    }

    public void touch(PersonalAccessToken t) {
        t.setLastUsedAt(Instant.now());
        tokens.save(t);
    }

    public void delete(PersonalAccessToken t) {
        tokens.delete(t);
    }

    /** $user->tokens()->delete() */
    public void deleteAll(Long userId) {
        tokens.deleteAllOfUser(userId);
    }

    /** Xóa mọi token khác, giữ token đang dùng. */
    public void deleteOthers(Long userId, Long keepId) {
        tokens.deleteOthersOfUser(userId, keepId);
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
