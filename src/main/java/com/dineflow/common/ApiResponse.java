package com.dineflow.common;

import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Khuôn phản hồi chung { status, message, data, meta? } (ApiController::ok của Laravel). */
public final class ApiResponse {
    private ApiResponse() {
    }

    public static ResponseEntity<Map<String, Object>> ok(Object data) {
        return ok(data, "Thành công", 200, null);
    }

    public static ResponseEntity<Map<String, Object>> ok(Object data, String message) {
        return ok(data, message, 200, null);
    }

    public static ResponseEntity<Map<String, Object>> ok(Object data, String message, int code) {
        return ok(data, message, code, null);
    }

    public static ResponseEntity<Map<String, Object>> ok(Object data, String message, int code, Map<String, Object> meta) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "success");
        body.put("message", message);
        body.put("data", data);
        if (meta != null && !meta.isEmpty()) body.put("meta", meta);
        return ResponseEntity.status(code).body(body);
    }

    public static ResponseEntity<Map<String, Object>> error(String message, int code, Object errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", message);
        body.put("data", null);
        if (errors != null) body.put("errors", errors);
        return ResponseEntity.status(code).body(body);
    }
}
