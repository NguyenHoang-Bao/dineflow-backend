package com.dineflow.security;

import com.dineflow.common.ApiException;
import com.dineflow.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/** Link có chữ ký + hạn dùng, giống URL::temporarySignedRoute() + middleware 'signed'. */
@Service
@RequiredArgsConstructor
public class SignedUrlService {
    private final AppProperties props;

    public String sign(String path, Map<String, String> params, Instant expires) {
        Map<String, String> all = new TreeMap<>(params);
        all.put("expires", String.valueOf(expires.getEpochSecond()));
        String query = query(all);
        String url = props.getUrl() + path + "?" + query;
        return url + "&signature=" + hmac(path + "?" + query);
    }

    /** Kiểm tra chữ ký: sai tham số hoặc hết hạn → 403 như Laravel. */
    public void verify(String path, Map<String, String> requestParams) {
        String signature = requestParams.get("signature");
        String expires = requestParams.get("expires");
        Map<String, String> all = new TreeMap<>(requestParams);
        all.remove("signature");
        String expected = hmac(path + "?" + query(all));
        boolean valid = signature != null && MessageDigest.isEqual(expected.getBytes(), signature.getBytes());
        boolean notExpired;
        try {
            notExpired = expires != null && Long.parseLong(expires) >= Instant.now().getEpochSecond();
        } catch (NumberFormatException e) {
            notExpired = false;
        }
        if (!valid || !notExpired) throw ApiException.forbidden("Invalid signature.");
    }

    private static String query(Map<String, String> params) {
        StringJoiner j = new StringJoiner("&");
        params.forEach((k, v) -> j.add(enc(k) + "=" + enc(v)));
        return j.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.getKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
