package com.dineflow.common;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lỗi trả về cho app theo khuôn { status: "error", message, data: null, errors? }.
 * Tương đương abort(...) và ValidationException::withMessages([...]) của Laravel.
 */
@Getter
public class ApiException extends RuntimeException {
    private final int status;
    private final Map<String, List<String>> errors;

    public ApiException(int status, String message) {
        this(status, message, null);
    }

    public ApiException(int status, String message, Map<String, List<String>> errors) {
        super(message);
        this.status = status;
        this.errors = errors;
    }

    /** Lỗi 422 gắn với một field, giống ValidationException::withMessages(['field' => 'msg']). */
    public static ApiException validation(String field, String message) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        errors.put(field, List.of(message));
        return new ApiException(422, message, errors);
    }

    /** Lỗi 422 nhiều field; message = thông báo đầu tiên. */
    public static ApiException validation(Map<String, String> fieldMessages) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        fieldMessages.forEach((k, v) -> errors.put(k, List.of(v)));
        return new ApiException(422, fieldMessages.values().iterator().next(), errors);
    }

    public static ApiException notFound() {
        return new ApiException(404, "Không tìm thấy dữ liệu yêu cầu.");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, message);
    }

    /** Lỗi nghiệp vụ không gắn field: $this->fail($message, 422). */
    public static ApiException fail(String message) {
        return new ApiException(422, message);
    }
}
